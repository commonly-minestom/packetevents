package io.github.retrooper.packetevents.minestom.netty.buffer;

import com.github.retrooper.packetevents.netty.buffer.ByteBufOperator;

import java.nio.charset.Charset;

public final class ByteBufOperatorImpl implements ByteBufOperator {

    @Override
    public int capacity(Object buffer) {
        return ((PacketBuffer) buffer).capacity();
    }

    @Override
    public Object capacity(Object buffer, int capacity) {
        ((PacketBuffer) buffer).capacity(capacity);
        return buffer;
    }

    @Override
    public int readerIndex(Object buffer) {
        return ((PacketBuffer) buffer).readerIndex();
    }

    @Override
    public Object readerIndex(Object buffer, int readerIndex) {
        ((PacketBuffer) buffer).readerIndex(readerIndex);
        return buffer;
    }

    @Override
    public int writerIndex(Object buffer) {
        return ((PacketBuffer) buffer).writerIndex();
    }

    @Override
    public Object writerIndex(Object buffer, int writerIndex) {
        ((PacketBuffer) buffer).writerIndex(writerIndex);
        return buffer;
    }

    @Override
    public int readableBytes(Object buffer) {
        return ((PacketBuffer) buffer).readableBytes();
    }

    @Override
    public int writableBytes(Object buffer) {
        return ((PacketBuffer) buffer).writableBytes();
    }

    @Override
    public Object clear(Object buffer) {
        ((PacketBuffer) buffer).clear();
        return buffer;
    }

    @Override
    public byte readByte(Object buffer) {
        return ((PacketBuffer) buffer).readByte();
    }

    @Override
    public short readShort(Object buffer) {
        return ((PacketBuffer) buffer).readShort();
    }

    @Override
    public int readMedium(Object buffer) {
        return ((PacketBuffer) buffer).readMedium();
    }

    @Override
    public int readInt(Object buffer) {
        return ((PacketBuffer) buffer).readInt();
    }

    @Override
    public long readUnsignedInt(Object buffer) {
        return ((PacketBuffer) buffer).readInt() & 0xFFFFFFFFL;
    }

    @Override
    public long readLong(Object buffer) {
        return ((PacketBuffer) buffer).readLong();
    }

    @Override
    public void writeByte(Object buffer, int value) {
        ((PacketBuffer) buffer).writeByte(value);
    }

    @Override
    public void writeShort(Object buffer, int value) {
        ((PacketBuffer) buffer).writeShort(value);
    }

    @Override
    public void writeShortLE(Object buffer, int value) {
        ((PacketBuffer) buffer).writeShortLE(value);
    }

    @Override
    public void writeMedium(Object buffer, int value) {
        ((PacketBuffer) buffer).writeMedium(value);
    }

    @Override
    public void writeInt(Object buffer, int value) {
        ((PacketBuffer) buffer).writeInt(value);
    }

    @Override
    public void writeLong(Object buffer, long value) {
        ((PacketBuffer) buffer).writeLong(value);
    }

    @Override
    public Object getBytes(Object buffer, int index, byte[] destination) {
        ((PacketBuffer) buffer).getBytes(index, destination);
        return buffer;
    }

    @Override
    public short getUnsignedByte(Object buffer, int index) {
        return ((PacketBuffer) buffer).getUnsignedByte(index);
    }

    @Override
    public boolean isReadable(Object buffer) {
        return ((PacketBuffer) buffer).isReadable();
    }

    @Override
    public Object copy(Object buffer) {
        return ((PacketBuffer) buffer).copy();
    }

    @Override
    public Object duplicate(Object buffer) {
        return ((PacketBuffer) buffer).duplicate();
    }

    @Override
    public boolean hasArray(Object buffer) {
        return false;
    }

    @Override
    public byte[] array(Object buffer) {
        throw new UnsupportedOperationException("Minestom buffers are not backed by an accessible array");
    }

    @Override
    public Object retain(Object buffer) {
        return ((PacketBuffer) buffer).retain();
    }

    @Override
    public Object retainedDuplicate(Object buffer) {
        return ((PacketBuffer) buffer).retainedDuplicate();
    }

    @Override
    public Object readSlice(Object buffer, int length) {
        return ((PacketBuffer) buffer).readSlice(length);
    }

    @Override
    public Object readBytes(Object buffer, byte[] destination, int destinationIndex, int length) {
        ((PacketBuffer) buffer).readBytes(destination, destinationIndex, length);
        return buffer;
    }

    @Override
    public Object readBytes(Object buffer, int length) {
        return ((PacketBuffer) buffer).readBytes(length);
    }

    @Override
    public void readBytes(Object buffer, byte[] bytes) {
        ((PacketBuffer) buffer).readBytes(bytes, 0, bytes.length);
    }

    @Override
    public Object writeBytes(Object buffer, Object source) {
        ((PacketBuffer) buffer).writeBytes((PacketBuffer) source);
        return buffer;
    }

    @Override
    public Object writeBytes(Object buffer, byte[] bytes) {
        ((PacketBuffer) buffer).writeBytes(bytes);
        return buffer;
    }

    @Override
    public Object writeBytes(Object buffer, byte[] bytes, int offset, int length) {
        ((PacketBuffer) buffer).writeBytes(bytes, offset, length);
        return buffer;
    }

    @Override
    public boolean release(Object buffer) {
        return ((PacketBuffer) buffer).release();
    }

    @Override
    public int refCnt(Object buffer) {
        return ((PacketBuffer) buffer).references();
    }

    @Override
    public Object skipBytes(Object buffer, int length) {
        ((PacketBuffer) buffer).skipBytes(length);
        return buffer;
    }

    @Override
    public String toString(Object buffer, int index, int length, Charset charset) {
        return ((PacketBuffer) buffer).toString(index, length, charset);
    }

    @Override
    public Object markReaderIndex(Object buffer) {
        ((PacketBuffer) buffer).markReaderIndex();
        return buffer;
    }

    @Override
    public Object resetReaderIndex(Object buffer) {
        ((PacketBuffer) buffer).resetReaderIndex();
        return buffer;
    }

    @Override
    public Object markWriterIndex(Object buffer) {
        ((PacketBuffer) buffer).markWriterIndex();
        return buffer;
    }

    @Override
    public Object resetWriterIndex(Object buffer) {
        ((PacketBuffer) buffer).resetWriterIndex();
        return buffer;
    }

    @Override
    public Object allocateNewBuffer(Object buffer) {
        return PacketBuffer.allocate();
    }
}
