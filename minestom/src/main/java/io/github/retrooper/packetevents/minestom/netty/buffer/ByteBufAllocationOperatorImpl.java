package io.github.retrooper.packetevents.minestom.netty.buffer;

import com.github.retrooper.packetevents.netty.buffer.ByteBufAllocationOperator;

public final class ByteBufAllocationOperatorImpl implements ByteBufAllocationOperator {

    @Override
    public Object wrappedBuffer(byte[] bytes) {
        return PacketBuffer.wrap(bytes);
    }

    @Override
    public Object copiedBuffer(byte[] bytes) {
        return PacketBuffer.copyOf(bytes);
    }

    @Override
    public Object buffer() {
        return PacketBuffer.allocate();
    }

    @Override
    public Object buffer(int initialCapacity) {
        return PacketBuffer.allocate(initialCapacity);
    }

    @Override
    public Object directBuffer() {
        return PacketBuffer.allocate();
    }

    @Override
    public Object directBuffer(int initialCapacity) {
        return PacketBuffer.allocate(initialCapacity);
    }

    @Override
    public Object compositeBuffer() {
        return PacketBuffer.allocate();
    }

    @Override
    public Object compositeBuffer(int maxNumComponents) {
        return PacketBuffer.allocate();
    }

    @Override
    public Object emptyBuffer() {
        return PacketBuffer.allocate(0);
    }
}
