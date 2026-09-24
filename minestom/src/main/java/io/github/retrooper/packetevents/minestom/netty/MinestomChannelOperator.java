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

package io.github.retrooper.packetevents.minestom.netty;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.netty.channel.ChannelOperator;
import io.github.retrooper.packetevents.minestom.network.PacketEventsPlayerConnection;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.net.SocketAddress;
import java.util.List;

// channels are PacketEventsPlayerConnection instances, pipeline contexts only tell whether packetevents is skipped
public final class MinestomChannelOperator implements ChannelOperator {

    @Override
    public SocketAddress remoteAddress(Object channel) {
        return connection(channel).getRemoteAddress();
    }

    @Override
    public SocketAddress localAddress(Object channel) {
        return connection(channel).getLocalAddress();
    }

    @Override
    public boolean isOpen(Object channel) {
        return channel instanceof PacketEventsPlayerConnection connection && connection.isOpen();
    }

    @Override
    public Object close(Object channel) {
        PacketEventsPlayerConnection connection = connection(channel);
        connection.disconnect();

        return connection.closeFuture();
    }

    @Override
    public Object write(Object channel, Object buffer) {
        connection(channel).writePacket((ByteBuf) buffer, false);

        return null;
    }

    // minestom flushes its queue on its own
    @Override
    public Object flush(Object channel) {
        return null;
    }

    @Override
    public Object writeAndFlush(Object channel, Object buffer) {
        return write(channel, buffer);
    }

    @Override
    public Object fireChannelRead(Object channel, Object buffer) {
        connection(channel).receivePacket((ByteBuf) buffer, false);

        return null;
    }

    @Override
    public Object writeInContext(Object channel, String ctx, Object buffer) {
        connection(channel).writePacket((ByteBuf) buffer, true);

        return null;
    }

    @Override
    public Object flushInContext(Object channel, String ctx) {
        return null;
    }

    @Override
    public Object writeAndFlushInContext(Object channel, String ctx, Object buffer) {
        return writeInContext(channel, ctx, buffer);
    }

    @Override
    public Object fireChannelReadInContext(Object channel, String ctx, Object buffer) {
        connection(channel).receivePacket((ByteBuf) buffer, true);

        return null;
    }

    @Override
    public List<String> pipelineHandlerNames(Object channel) {
        return List.of(PacketEvents.DECODER_NAME, PacketEvents.ENCODER_NAME);
    }

    @Override
    public Object getPipelineHandler(Object channel, String name) {
        return connection(channel).handler(name);
    }

    @Override
    public Object getPipelineContext(Object channel, String name) {
        return connection(channel).handler(name);
    }

    @Override
    public Object getPipeline(Object channel) {
        return channel;
    }

    @Override
    public void runInEventLoop(Object channel, Runnable runnable) {
        connection(channel).execute(runnable);
    }

    @Override
    public Object pooledByteBuf(Object channel) {
        return ByteBufAllocator.DEFAULT.buffer();
    }

    private static PacketEventsPlayerConnection connection(Object channel) {
        if (channel instanceof PacketEventsPlayerConnection connection) return connection;

        throw new IllegalArgumentException("Not a packetevents connection: " + channel);
    }
}
