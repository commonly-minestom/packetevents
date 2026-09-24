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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import net.minestom.server.ServerFlag;
import net.minestom.server.network.packet.PacketReading;
import net.minestom.server.network.packet.PacketVanilla;
import net.minestom.server.network.packet.client.login.ClientEncryptionResponsePacket;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;

// socket -> decrypt -> split -> inflate -> packetevents -> plaintext frames for minestom
final class PacketDecoder {

    private static final int ENCRYPTION_RESPONSE_ID = PacketVanilla.CLIENT_PACKET_PARSER.login()
            .packetInfo(ClientEncryptionResponsePacket.class).id();
    private static final int MIN_READ_SIZE = 8192;

    private final PacketEventsPlayerConnection connection;
    private final SocketChannel socket;
    private final InboundPipe pipe;

    private volatile @Nullable Cipher pendingCipher;
    private volatile int compressionThreshold = -1;

    private @UnknownNullability ByteBuf input;
    private @Nullable Cipher cipher;
    private boolean proxyHeaderPending = ServerFlag.PROXY_PROTOCOL;
    private @Nullable ByteBuf batch;

    PacketDecoder(PacketEventsPlayerConnection connection, SocketChannel socket, InboundPipe pipe) {
        this.connection = connection;
        this.socket = socket;
        this.pipe = pipe;
    }

    void run() {
        input = ByteBufAllocator.DEFAULT.directBuffer(ServerFlag.POOLED_BUFFER_SIZE);

        try {
            while (pipe.awaitDemand()) {
                if (!decodeBatch()) return;
            }
        } catch (ClosedChannelException | InterruptedException _) {
            pipe.close(null);
        } catch (IOException e) {
            pipe.close(e);
        } catch (Throwable t) {
            pipe.close(new IOException("Failed to decode packets", t));
        } finally {
            input.release();
        }
    }

    void enableDecryption(Cipher cipher) {
        this.pendingCipher = cipher;
    }

    void enableDecompression(int threshold) {
        this.compressionThreshold = threshold;
    }

    boolean isDecoding() {
        return batch != null;
    }

    // a packet received from a packetevents listener while decoding runs before the current one, like netty
    void decodeNested(ByteBuf packet, boolean silent) {
        ByteBuf output = batch;

        try {
            if (!silent) connection.handleInbound(packet);

            if (output != null && packet.isReadable()) writeFrame(output, packet);
        } finally {
            packet.release();
        }
    }

    static void writeFrame(ByteBuf output, ByteBuf packet) {
        VarInts.write(output, packet.readableBytes());
        output.writeBytes(packet, packet.readerIndex(), packet.readableBytes());
    }

    private boolean decodeBatch() throws IOException {
        ByteBuf output = ByteBufAllocator.DEFAULT.heapBuffer();

        try {
            while (true) {
                activateCipher();
                decodeFrames(output);

                if (output.isReadable()) return pipe.offer(output);

                if (!readSocket()) {
                    pipe.end();

                    return false;
                }
            }
        } finally {
            output.release();
        }
    }

    private void decodeFrames(ByteBuf output) throws IOException {
        connection.lockEvents();
        batch = output;

        try {
            if (proxyHeaderPending && !forwardProxyHeader(output)) return;

            ByteBuf packet;

            while ((packet = nextPacket()) != null) {
                // bytes after an encryption response are encrypted, let minestom install the key first
                if (forward(packet, output)) return;
            }
        } finally {
            batch = null;
            connection.unlockEvents();
        }
    }

    private boolean forwardProxyHeader(ByteBuf output) throws IOException {
        int length = ProxyProtocolHeader.length(input);
        if (length == ProxyProtocolHeader.NEED_MORE) return false;

        proxyHeaderPending = false;
        output.writeBytes(input, length);

        return true;
    }

    private @Nullable ByteBuf nextPacket() throws IOException {
        VarInts.VarInt length = VarInts.peek(input, input.readerIndex());
        if (length == null) return null;

        int maxLength = PacketReading.maxPacketSize(connection.getClientState());
        if (length.value() < 0 || length.value() > maxLength) throw new IOException("Invalid packet length " + length.value());

        if (input.readableBytes() < length.size() + length.value()) return null;

        input.skipBytes(length.size());
        ByteBuf frame = input.readSlice(length.value());

        if (compressionThreshold < 0) return copy(frame);

        int dataLength = VarInts.read(frame);
        if (dataLength == 0) return copy(frame);

        if (dataLength < 0 || dataLength > maxLength) throw new IOException("Invalid decompressed length " + dataLength);

        ByteBuf packet = ByteBufAllocator.DEFAULT.buffer(dataLength);

        try {
            PacketCompression.inflate(frame, packet, dataLength);
        } catch (IOException e) {
            packet.release();
            throw e;
        }

        return packet;
    }

    private boolean forward(ByteBuf packet, ByteBuf output) throws IOException {
        try {
            connection.handleInbound(packet);
            if (!packet.isReadable()) return false;

            VarInts.VarInt packetId = VarInts.peek(packet, packet.readerIndex());
            writeFrame(output, packet);

            return cipher == null && packetId != null && packetId.value() == ENCRYPTION_RESPONSE_ID;
        } finally {
            packet.release();
        }
    }

    private boolean readSocket() throws IOException {
        input.discardSomeReadBytes();
        input.ensureWritable(MIN_READ_SIZE);

        int start = input.writerIndex();
        int read = socket.read(input.nioBuffer(start, input.writableBytes()));
        if (read < 0) return false;

        input.writerIndex(start + read);

        if (cipher != null) decrypt(cipher, start, read);
        else activateCipher();

        return true;
    }

    // everything not decoded yet was sent after the key exchange, so it's all ciphertext
    private void activateCipher() throws IOException {
        Cipher pending = pendingCipher;
        if (cipher != null || pending == null) return;

        cipher = pending;

        if (input.isReadable()) decrypt(pending, input.readerIndex(), input.readableBytes());
    }

    private void decrypt(Cipher cipher, int index, int length) throws IOException {
        ByteBuffer region = input.nioBuffer(index, length);

        try {
            cipher.update(region, region.duplicate());
        } catch (ShortBufferException e) {
            throw new IOException("Failed to decrypt packet data", e);
        }
    }

    private static ByteBuf copy(ByteBuf frame) {
        ByteBuf packet = ByteBufAllocator.DEFAULT.buffer(frame.readableBytes());
        packet.writeBytes(frame);

        return packet;
    }
}
