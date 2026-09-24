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
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

final class VarInts {

    static final int MAX_FIXED_3 = (1 << 21) - 1;

    private static final int MAX_SIZE = 5;

    private VarInts() {
    }

    // null when the varint isn't fully buffered yet
    static @Nullable VarInt peek(ByteBuf buffer, int index) throws IOException {
        int value = 0;
        int end = buffer.writerIndex();

        for (int size = 0; size < MAX_SIZE; size++) {
            if (index + size >= end) return null;

            byte current = buffer.getByte(index + size);
            value |= (current & 0x7F) << (size * 7);

            if ((current & 0x80) == 0) return new VarInt(value, size + 1);
        }

        throw new IOException("VarInt is too big");
    }

    static int read(ByteBuf buffer) throws IOException {
        VarInt varInt = peek(buffer, buffer.readerIndex());
        if (varInt == null) throw new IOException("VarInt is truncated");

        buffer.skipBytes(varInt.size());

        return varInt.value();
    }

    static void write(ByteBuf buffer, int value) {
        while ((value & ~0x7F) != 0) {
            buffer.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }

        buffer.writeByte(value);
    }

    // zero padded to 3 bytes so frame lengths can be patched in after the body is written
    static void setFixed3(ByteBuf buffer, int index, int value) {
        buffer.setByte(index, (value & 0x7F) | 0x80);
        buffer.setByte(index + 1, ((value >>> 7) & 0x7F) | 0x80);
        buffer.setByte(index + 2, value >>> 14);
    }

    record VarInt(int value, int size) {
    }
}
