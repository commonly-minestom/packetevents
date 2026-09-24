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
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

// hands decoded frames to minestom's read thread, which only asks for more once it processed the last batch
final class InboundPipe {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition dataAvailable = lock.newCondition();
    private final Condition dataRequested = lock.newCondition();
    private final ByteBuf pending = Unpooled.buffer();

    private boolean waiting;
    private boolean ended;
    private @Nullable IOException failure;

    int read(ByteBuffer destination) throws IOException, InterruptedException {
        if (!destination.hasRemaining()) return 0;

        lock.lockInterruptibly();

        try {
            while (!pending.isReadable()) {
                if (failure != null) throw failure;
                if (ended) return -1;

                waiting = true;
                dataRequested.signal();
                dataAvailable.await();
            }

            waiting = false;
            int length = Math.min(destination.remaining(), pending.readableBytes());
            int limit = destination.limit();

            destination.limit(destination.position() + length);
            pending.readBytes(destination);
            destination.limit(limit);

            if (!pending.isReadable()) pending.clear();

            return length;
        } finally {
            lock.unlock();
        }
    }

    // returns false once the pipe can't accept data anymore
    boolean awaitDemand() throws InterruptedException {
        lock.lockInterruptibly();

        try {
            while (!ended && (!waiting || pending.isReadable())) dataRequested.await();

            return !ended;
        } finally {
            lock.unlock();
        }
    }

    boolean offer(ByteBuf frames) {
        lock.lock();

        try {
            if (ended) return false;

            pending.writeBytes(frames, frames.readerIndex(), frames.readableBytes());
            dataAvailable.signal();

            return true;
        } finally {
            lock.unlock();
        }
    }

    // end of stream, frames already offered are still delivered
    void end() {
        lock.lock();

        try {
            ended = true;
            dataAvailable.signalAll();
            dataRequested.signalAll();
        } finally {
            lock.unlock();
        }
    }

    void close(@Nullable IOException cause) {
        lock.lock();

        try {
            if (failure == null) failure = cause;

            ended = true;
            pending.clear();
            dataAvailable.signalAll();
            dataRequested.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
