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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// finds where a haproxy PROXY header ends, minestom parses the header itself
final class ProxyProtocolHeader {

    static final int NEED_MORE = -1;
    static final int ABSENT = 0;

    private static final byte[] V1_PREFIX = "PROXY ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] V2_SIGNATURE = {0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A};
    private static final int V1_MAX_LENGTH = 107;
    private static final int V2_FIXED_LENGTH = 16;

    private ProxyProtocolHeader() {
    }

    static int length(ByteBuf buffer) throws IOException {
        if (!buffer.isReadable()) return NEED_MORE;

        if (startsWith(buffer, V2_SIGNATURE)) return v2Length(buffer);

        if (startsWith(buffer, V1_PREFIX)) return v1Length(buffer);

        return ABSENT;
    }

    // compares only what is buffered so a split signature still matches
    private static boolean startsWith(ByteBuf buffer, byte[] signature) {
        int start = buffer.readerIndex();
        int length = Math.min(buffer.readableBytes(), signature.length);

        for (int i = 0; i < length; i++) {
            if (buffer.getByte(start + i) != signature[i]) return false;
        }

        return true;
    }

    private static int v2Length(ByteBuf buffer) {
        if (buffer.readableBytes() < V2_FIXED_LENGTH) return NEED_MORE;

        int length = V2_FIXED_LENGTH + buffer.getUnsignedShort(buffer.readerIndex() + 14);

        return buffer.readableBytes() >= length ? length : NEED_MORE;
    }

    private static int v1Length(ByteBuf buffer) throws IOException {
        int start = buffer.readerIndex();
        int limit = Math.min(buffer.readableBytes(), V1_MAX_LENGTH);

        for (int i = 1; i < limit; i++) {
            if (buffer.getByte(start + i - 1) == '\r' && buffer.getByte(start + i) == '\n') return i + 1;
        }

        if (buffer.readableBytes() < V1_MAX_LENGTH) return NEED_MORE;

        throw new IOException("Invalid PROXY protocol header");
    }
}
