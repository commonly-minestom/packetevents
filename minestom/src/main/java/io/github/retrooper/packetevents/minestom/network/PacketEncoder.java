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
import io.netty.buffer.Unpooled;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.PacketVanilla;
import net.minestom.server.network.packet.server.login.LoginSuccessPacket;
import net.minestom.server.network.packet.server.login.SetCompressionPacket;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

// plaintext frames from minestom -> packetevents -> deflate -> encrypt -> socket
final class PacketEncoder {

    // never a real packet id, marks packets sent silently through packetevents
    static final int SILENT_MARKER = -1;

    private static final int LOGIN_SUCCESS_ID = PacketVanilla.SERVER_PACKET_PARSER.login()
            .packetInfo(LoginSuccessPacket.class).id();
    private static final int SET_COMPRESSION_ID = PacketVanilla.SERVER_PACKET_PARSER.login()
            .packetInfo(SetCompressionPacket.class).id();

    private final PacketEventsPlayerConnection connection;
    private final SocketChannel socket;
    private final int compressionThreshold;
    private final ByteBuf remainder = Unpooled.buffer(0);
    private final List<Runnable> afterSend = new ArrayList<>();

    private volatile @Nullable Cipher pendingCipher;

    private @Nullable Cipher cipher;
    private int activeThreshold = -1;
    private @Nullable ByteBuf output;

    PacketEncoder(PacketEventsPlayerConnection connection, SocketChannel socket, int compressionThreshold) {
        this.connection = connection;
        this.socket = socket;
        this.compressionThreshold = compressionThreshold;
    }

    int write(ByteBuffer source) throws IOException {
        int consumed = source.remaining();
        ByteBuf input = Unpooled.wrappedBuffer(source);
        source.position(source.limit());

        if (remainder.isReadable()) input = remainder.writeBytes(input);

        ByteBuf encoded = ByteBufAllocator.DEFAULT.directBuffer(input.readableBytes() + 64);

        try {
            int encryptFrom = encodeFrames(input, encoded);
            keepRemainder(input);

            if (encryptFrom >= 0) encrypt(encoded, encryptFrom);

            flush(encoded);
        } catch (IOException | RuntimeException e) {
            afterSend.clear();
            throw e;
        } finally {
            encoded.release();
        }

        runAfterSend();

        return consumed;
    }

    void enableEncryption(Cipher cipher) {
        this.pendingCipher = cipher;
    }

    boolean isEncryptionEnabled() {
        return pendingCipher != null;
    }

    boolean isEncoding() {
        return output != null;
    }

    // a packet sent from a packetevents listener while encoding goes out before the current one, like netty
    void encodeNested(ByteBuf packet, boolean silent) throws IOException {
        encodePacket(packet, silent);
    }

    private int encodeFrames(ByteBuf input, ByteBuf encoded) throws IOException {
        int encryptFrom = -1;
        connection.lockEvents();
        output = encoded;

        try {
            while (true) {
                VarInts.VarInt length = VarInts.peek(input, input.readerIndex());
                if (length == null || input.readableBytes() < length.size() + length.value()) return encryptFrom;

                input.skipBytes(length.size());
                ByteBuf frame = input.readSlice(length.value());

                if (encryptFrom < 0 && activateCipher() != null) encryptFrom = encoded.writerIndex();

                encodeFrame(frame);
            }
        } finally {
            output = null;
            connection.unlockEvents();
        }
    }

    private void encodeFrame(ByteBuf frame) throws IOException {
        VarInts.VarInt marker = VarInts.peek(frame, frame.readerIndex());
        boolean silent = marker != null && marker.value() == SILENT_MARKER;

        if (silent) frame.skipBytes(marker.size());

        ByteBuf packet = ByteBufAllocator.DEFAULT.buffer(frame.readableBytes());
        packet.writeBytes(frame);

        encodePacket(packet, silent);
    }

    private void encodePacket(ByteBuf packet, boolean silent) throws IOException {
        try {
            if (activeThreshold < 0 && isLoginSuccess(packet)) startCompression();

            if (!silent) connection.handleOutbound(packet, afterSend);

            if (packet.isReadable()) writeFrame(packet);
        } finally {
            packet.release();
        }
    }

    // minestom never compresses on its own, the threshold is announced right before login success like vanilla
    private boolean isLoginSuccess(ByteBuf packet) throws IOException {
        if (compressionThreshold <= 0 || connection.getClientState() != ConnectionState.LOGIN) return false;

        VarInts.VarInt packetId = VarInts.peek(packet, packet.readerIndex());

        return packetId != null && packetId.value() == LOGIN_SUCCESS_ID;
    }

    private void startCompression() throws IOException {
        ByteBuf packet = ByteBufAllocator.DEFAULT.buffer();

        try {
            VarInts.write(packet, SET_COMPRESSION_ID);
            VarInts.write(packet, compressionThreshold);

            connection.handleOutbound(packet, afterSend);
            if (!packet.isReadable()) return;

            ByteBuf view = packet.duplicate();
            VarInts.read(view);
            int threshold = VarInts.read(view);

            writeFrame(packet);
            if (threshold < 0) return;

            activeThreshold = threshold;
            connection.decoder().enableDecompression(threshold);
        } finally {
            packet.release();
        }
    }

    private void writeFrame(ByteBuf packet) throws IOException {
        ByteBuf encoded = output;
        if (encoded == null) throw new IllegalStateException("Not encoding");

        int length = packet.readableBytes();

        if (activeThreshold < 0) {
            VarInts.write(encoded, length);
            encoded.writeBytes(packet, packet.readerIndex(), length);

            return;
        }

        int start = encoded.writerIndex();
        encoded.writeMedium(0);

        if (length < activeThreshold) {
            encoded.writeByte(0);
            encoded.writeBytes(packet, packet.readerIndex(), length);
        } else {
            VarInts.write(encoded, length);
            PacketCompression.deflate(packet, encoded);
        }

        int frameLength = encoded.writerIndex() - start - 3;
        if (frameLength > VarInts.MAX_FIXED_3) throw new IOException("Packet is too large to be sent (" + frameLength + " bytes)");

        VarInts.setFixed3(encoded, start, frameLength);
    }

    private @Nullable Cipher activateCipher() {
        if (cipher == null) cipher = pendingCipher;

        return cipher;
    }

    private void encrypt(ByteBuf encoded, int from) throws IOException {
        Cipher active = cipher;
        if (active == null) return;

        ByteBuffer region = encoded.nioBuffer(from, encoded.writerIndex() - from);

        try {
            active.update(region, region.duplicate());
        } catch (ShortBufferException e) {
            throw new IOException("Failed to encrypt packet data", e);
        }
    }

    private void flush(ByteBuf encoded) throws IOException {
        ByteBuffer buffer = encoded.nioBuffer();

        while (buffer.hasRemaining()) socket.write(buffer);
    }

    private void keepRemainder(ByteBuf input) {
        if (input == remainder) {
            remainder.discardReadBytes();

            return;
        }

        if (input.isReadable()) remainder.writeBytes(input);
    }

    private void runAfterSend() {
        if (afterSend.isEmpty()) return;

        List<Runnable> tasks = List.copyOf(afterSend);
        afterSend.clear();

        connection.runLocked(tasks);
    }
}
