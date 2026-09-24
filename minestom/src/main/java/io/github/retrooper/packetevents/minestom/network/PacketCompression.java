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
import net.minestom.server.utils.ObjectPool;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

final class PacketCompression {

    private static final int DEFLATE_CHUNK = 8192;

    private static final ObjectPool<Deflater> DEFLATERS = ObjectPool.pool(Deflater::new, deflater -> {
        deflater.reset();

        return deflater;
    });

    private static final ObjectPool<Inflater> INFLATERS = ObjectPool.pool(Inflater::new, inflater -> {
        inflater.reset();

        return inflater;
    });

    private PacketCompression() {
    }

    static void deflate(ByteBuf input, ByteBuf output) {
        Deflater deflater = DEFLATERS.get();

        try {
            deflater.setInput(input.nioBuffer());
            deflater.finish();

            while (!deflater.finished()) {
                output.ensureWritable(DEFLATE_CHUNK);

                int written = deflater.deflate(output.nioBuffer(output.writerIndex(), output.writableBytes()));
                output.writerIndex(output.writerIndex() + written);
            }
        } finally {
            DEFLATERS.add(deflater);
        }
    }

    static void inflate(ByteBuf input, ByteBuf output, int length) throws IOException {
        Inflater inflater = INFLATERS.get();

        try {
            inflater.setInput(input.nioBuffer());
            output.ensureWritable(length);

            ByteBuffer target = output.nioBuffer(output.writerIndex(), length);
            int written = 0;

            while (written < length && !inflater.finished()) {
                int inflated = inflater.inflate(target);
                if (inflated == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;

                written += inflated;
            }

            if (written != length || !inflater.finished()) throw new IOException("Badly compressed packet, expected " + length + " bytes");

            output.writerIndex(output.writerIndex() + written);
        } catch (DataFormatException e) {
            throw new IOException("Badly compressed packet", e);
        } finally {
            INFLATERS.add(inflater);
        }
    }
}
