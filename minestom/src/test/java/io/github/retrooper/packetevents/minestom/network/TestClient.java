/*
 * This file is part of packetevents - https://github.com/retrooper/packetevents
 * Copyright (C) 2026 retrooper and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.github.retrooper.packetevents.minestom.network;

import net.minestom.server.MinecraftServer;
import net.minestom.server.extras.mojangAuth.MojangCrypt;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.PacketReading;
import net.minestom.server.network.packet.PacketVanilla;
import net.minestom.server.network.packet.PacketWriting;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.configuration.ClientFinishConfigurationPacket;
import net.minestom.server.network.packet.client.configuration.ClientSelectKnownPacksPacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.configuration.FinishConfigurationPacket;
import net.minestom.server.network.packet.server.configuration.SelectKnownPacksPacket;
import net.minestom.server.network.packet.server.login.SetCompressionPacket;
import net.minestom.server.network.packet.server.play.JoinGamePacket;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.zip.DataFormatException;

// minimal vanilla-like client built on minestom's own packet codecs
final class TestClient implements AutoCloseable {

    private static final int READ_SIZE = 1 << 16;

    private final SocketChannel channel;
    private final NetworkBuffer input = NetworkBuffer.resizableBuffer(READ_SIZE, MinecraftServer.getRegistries());

    private ConnectionState writeState = ConnectionState.HANDSHAKE;
    private ConnectionState readState = ConnectionState.HANDSHAKE;
    private int compressionThreshold;
    private @Nullable Cipher encryptCipher;
    private @Nullable Cipher decryptCipher;

    private TestClient(SocketChannel channel) {
        this.channel = channel;
    }

    static TestClient connect(int port) throws IOException {
        return new TestClient(SocketChannel.open(new InetSocketAddress(InetAddress.getLoopbackAddress(), port)));
    }

    void send(ClientPacket packet) throws IOException {
        NetworkBuffer buffer = NetworkBuffer.resizableBuffer(256, MinecraftServer.getRegistries());
        PacketWriting.writeFramedPacket(buffer, writeState, packet, compressionThreshold);

        if (encryptCipher != null) buffer.cipher(encryptCipher, 0, buffer.writeIndex());

        while (!buffer.writeChannel(channel)) Thread.onSpinWait();

        ConnectionState next = PacketVanilla.nextClientState(packet, writeState);
        if (writeState == ConnectionState.HANDSHAKE) readState = next;

        writeState = next;
    }

    ServerPacket receive() throws IOException {
        while (true) {
            if (input.readableBytes() > 0) {
                ServerPacket packet = parse();
                if (packet != null) return packet;
            }

            readMore();
        }
    }

    void sendRaw(byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        while (buffer.hasRemaining()) channel.write(buffer);
    }

    // answers what minestom waits for during configuration and returns once the player joined
    void completeConfiguration() throws IOException {
        while (true) {
            ServerPacket packet = receive();

            if (packet instanceof SelectKnownPacksPacket) send(new ClientSelectKnownPacksPacket(List.of(SelectKnownPacksPacket.MINECRAFT_CORE)));
            if (packet instanceof FinishConfigurationPacket) break;
        }

        send(new ClientFinishConfigurationPacket());
        receiveUntil(JoinGamePacket.class);
    }

    <T extends ServerPacket> T receiveUntil(Class<T> type) throws IOException {
        while (true) {
            ServerPacket packet = receive();

            if (type.isInstance(packet)) return type.cast(packet);
        }
    }

    void enableEncryption(SecretKey key) {
        encryptCipher = MojangCrypt.getCipher(Cipher.ENCRYPT_MODE, key);
        decryptCipher = MojangCrypt.getCipher(Cipher.DECRYPT_MODE, key);
    }

    int compressionThreshold() {
        return compressionThreshold;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    private @Nullable ServerPacket parse() throws IOException {
        PacketReading.Result<ServerPacket> result;

        try {
            result = PacketReading.readServer(input, readState, compressionThreshold > 0);
        } catch (DataFormatException e) {
            throw new IOException(e);
        }

        if (!(result instanceof PacketReading.Result.Success<ServerPacket> success)) return null;

        PacketReading.ParsedPacket<ServerPacket> parsed = success.packets().getFirst();
        readState = parsed.nextState();

        if (parsed.packet() instanceof SetCompressionPacket(int threshold)) compressionThreshold = threshold;

        return parsed.packet();
    }

    private void readMore() throws IOException {
        input.compact();

        if (input.writableBytes() < READ_SIZE) input.resize(input.capacity() + READ_SIZE);

        long start = input.writeIndex();
        int read = input.readChannel(channel);

        if (decryptCipher != null && read > 0) input.cipher(decryptCipher, start, read);
    }
}
