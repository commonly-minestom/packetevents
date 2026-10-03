package io.github.retrooper.packetevents.minestom.netty.channel;

import com.github.retrooper.packetevents.netty.channel.ChannelOperator;
import io.github.retrooper.packetevents.minestom.connection.PacketConnection;
import io.github.retrooper.packetevents.minestom.netty.buffer.PacketBuffer;

import java.net.SocketAddress;
import java.util.List;

public final class ChannelOperatorImpl implements ChannelOperator {

    @Override
    public SocketAddress remoteAddress(Object channel) {
        return ((PacketConnection) channel).remoteAddress();
    }

    @Override
    public SocketAddress localAddress(Object channel) {
        return ((PacketConnection) channel).localAddress();
    }

    @Override
    public boolean isOpen(Object channel) {
        return ((PacketConnection) channel).isOpen();
    }

    @Override
    public Object close(Object channel) {
        ((PacketConnection) channel).close();
        return channel;
    }

    @Override
    public Object write(Object channel, Object buffer) {
        ((PacketConnection) channel).send((PacketBuffer) buffer, false);
        return channel;
    }

    @Override
    public Object flush(Object channel) {
        return channel;
    }

    @Override
    public Object writeAndFlush(Object channel, Object buffer) {
        return write(channel, buffer);
    }

    @Override
    public Object fireChannelRead(Object channel, Object buffer) {
        ((PacketConnection) channel).receive((PacketBuffer) buffer, false);
        return channel;
    }

    @Override
    public Object writeInContext(Object channel, String ctx, Object buffer) {
        ((PacketConnection) channel).send((PacketBuffer) buffer, true);
        return channel;
    }

    @Override
    public Object flushInContext(Object channel, String ctx) {
        return channel;
    }

    @Override
    public Object writeAndFlushInContext(Object channel, String ctx, Object buffer) {
        return writeInContext(channel, ctx, buffer);
    }

    @Override
    public Object fireChannelReadInContext(Object channel, String ctx, Object buffer) {
        ((PacketConnection) channel).receive((PacketBuffer) buffer, true);
        return channel;
    }

    @Override
    public List<String> pipelineHandlerNames(Object channel) {
        return List.of();
    }

    @Override
    public Object getPipelineHandler(Object channel, String name) {
        return null;
    }

    @Override
    public Object getPipelineContext(Object channel, String name) {
        return null;
    }

    @Override
    public Object getPipeline(Object channel) {
        return channel;
    }

    @Override
    public void runInEventLoop(Object channel, Runnable runnable) {
        ((PacketConnection) channel).execute(runnable);
    }

    @Override
    public Object pooledByteBuf(Object channel) {
        return PacketBuffer.allocate();
    }
}
