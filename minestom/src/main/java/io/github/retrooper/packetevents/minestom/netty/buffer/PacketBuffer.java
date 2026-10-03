package io.github.retrooper.packetevents.minestom.netty.buffer;

import net.minestom.server.network.NetworkBuffer;

import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public final class PacketBuffer {

    private static final int DEFAULT_CAPACITY = 256;
    private static final AtomicIntegerFieldUpdater<PacketBuffer> REFERENCES =
            AtomicIntegerFieldUpdater.newUpdater(PacketBuffer.class, "references");

    private final NetworkBuffer buffer;
    private int markedReaderIndex;
    private int markedWriterIndex;
    private volatile int references = 1;

    public PacketBuffer(NetworkBuffer buffer) {
        this.buffer = buffer;
    }

    public static PacketBuffer allocate() {
        return allocate(DEFAULT_CAPACITY);
    }

    public static PacketBuffer allocate(int capacity) {
        return new PacketBuffer(NetworkBuffer.resizableBuffer(capacity));
    }

    public static PacketBuffer wrap(byte[] bytes) {
        return new PacketBuffer(NetworkBuffer.wrap(bytes, 0, bytes.length));
    }

    public static PacketBuffer copyOf(byte[] bytes) {
        PacketBuffer copy = allocate(bytes.length);
        copy.writeBytes(bytes);
        return copy;
    }

    public NetworkBuffer buffer() {
        return this.buffer;
    }

    public int capacity() {
        return (int) Math.min(this.buffer.capacity(), Integer.MAX_VALUE);
    }

    public void capacity(int capacity) {
        NetworkBuffer buffer = this.buffer;
        if (capacity > buffer.capacity()) {
            buffer.resize(capacity);
            return;
        }
        if (buffer.writeIndex() > capacity) {
            buffer.writeIndex(capacity);
        }
        if (buffer.readIndex() > capacity) {
            buffer.readIndex(capacity);
        }
    }

    public int readerIndex() {
        return (int) this.buffer.readIndex();
    }

    public void readerIndex(int index) {
        this.buffer.readIndex(index);
    }

    public int writerIndex() {
        return (int) this.buffer.writeIndex();
    }

    public void writerIndex(int index) {
        this.buffer.writeIndex(index);
    }

    public int readableBytes() {
        return (int) this.buffer.readableBytes();
    }

    public int writableBytes() {
        return (int) Math.min(this.buffer.writableBytes(), Integer.MAX_VALUE);
    }

    public boolean isReadable() {
        return this.buffer.readableBytes() > 0;
    }

    public void clear() {
        this.buffer.clear();
    }

    public void skipBytes(int length) {
        NetworkBuffer buffer = this.buffer;
        buffer.ensureReadable(length);
        buffer.advanceRead(length);
    }

    public byte readByte() {
        NetworkBuffer buffer = this.buffer;
        buffer.ensureReadable(Byte.BYTES);
        return buffer.read(NetworkBuffer.BYTE);
    }

    public short readShort() {
        NetworkBuffer buffer = this.buffer;
        buffer.ensureReadable(Short.BYTES);
        return buffer.read(NetworkBuffer.SHORT);
    }

    public int readMedium() {
        NetworkBuffer buffer = this.buffer;
        buffer.ensureReadable(3);
        int value = (buffer.read(NetworkBuffer.BYTE) & 0xFF) << 16
                | (buffer.read(NetworkBuffer.BYTE) & 0xFF) << 8
                | buffer.read(NetworkBuffer.BYTE) & 0xFF;
        return (value & 0x800000) == 0 ? value : value | 0xFF000000;
    }

    public int readInt() {
        NetworkBuffer buffer = this.buffer;
        buffer.ensureReadable(Integer.BYTES);
        return buffer.read(NetworkBuffer.INT);
    }

    public long readLong() {
        NetworkBuffer buffer = this.buffer;
        buffer.ensureReadable(Long.BYTES);
        return buffer.read(NetworkBuffer.LONG);
    }

    public void writeByte(int value) {
        this.buffer.write(NetworkBuffer.BYTE, (byte) value);
    }

    public void writeShort(int value) {
        this.buffer.write(NetworkBuffer.SHORT, (short) value);
    }

    public void writeShortLE(int value) {
        this.buffer.write(NetworkBuffer.SHORT, Short.reverseBytes((short) value));
    }

    public void writeMedium(int value) {
        NetworkBuffer buffer = this.buffer;
        buffer.write(NetworkBuffer.BYTE, (byte) (value >> 16));
        buffer.write(NetworkBuffer.BYTE, (byte) (value >> 8));
        buffer.write(NetworkBuffer.BYTE, (byte) value);
    }

    public void writeInt(int value) {
        this.buffer.write(NetworkBuffer.INT, value);
    }

    public void writeLong(long value) {
        this.buffer.write(NetworkBuffer.LONG, value);
    }

    public short getUnsignedByte(int index) {
        NetworkBuffer buffer = this.buffer;
        checkIndex(buffer, index, Byte.BYTES);
        return (short) (buffer.readAt(index, NetworkBuffer.BYTE) & 0xFF);
    }

    public void getBytes(int index, byte[] destination) {
        NetworkBuffer buffer = this.buffer;
        checkIndex(buffer, index, destination.length);
        buffer.copyTo(index, destination, 0, destination.length);
    }

    public void readBytes(byte[] destination, int offset, int length) {
        NetworkBuffer buffer = this.buffer;
        buffer.ensureReadable(length);
        buffer.copyTo(buffer.readIndex(), destination, offset, length);
        buffer.advanceRead(length);
    }

    public PacketBuffer readBytes(int length) {
        NetworkBuffer buffer = this.buffer;
        buffer.ensureReadable(length);
        PacketBuffer copy = copy(buffer.readIndex(), length, 0, length);
        buffer.advanceRead(length);
        return copy;
    }

    public PacketBuffer readSlice(int length) {
        NetworkBuffer buffer = this.buffer;
        buffer.ensureReadable(length);
        NetworkBuffer slice = buffer.slice(buffer.readIndex(), length, 0, length);
        buffer.advanceRead(length);
        return new PacketBuffer(slice);
    }

    public void writeBytes(PacketBuffer source) {
        NetworkBuffer from = source.buffer;
        long length = from.readableBytes();
        append(from, from.readIndex(), length);
        from.advanceRead(length);
    }

    public void writeBytes(byte[] bytes) {
        this.buffer.write(NetworkBuffer.RAW_BYTES, bytes);
    }

    public void writeBytes(byte[] bytes, int offset, int length) {
        if (offset == 0 && length == bytes.length) {
            writeBytes(bytes);
            return;
        }
        append(NetworkBuffer.wrap(bytes, 0, 0), offset, length);
    }

    public void append(NetworkBuffer source, long index, long length) {
        NetworkBuffer buffer = this.buffer;
        buffer.ensureWritable(length);
        NetworkBuffer.copy(source, index, buffer, buffer.writeIndex(), length);
        buffer.advanceWrite(length);
    }

    public PacketBuffer copy() {
        NetworkBuffer buffer = this.buffer;
        long length = buffer.readableBytes();
        return copy(buffer.readIndex(), length, 0, length);
    }

    public PacketBuffer duplicate() {
        NetworkBuffer buffer = this.buffer;
        return new PacketBuffer(buffer.slice(0, buffer.capacity(), buffer.readIndex(), buffer.writeIndex()));
    }

    public PacketBuffer retainedDuplicate() {
        NetworkBuffer buffer = this.buffer;
        return copy(0, buffer.writeIndex(), buffer.readIndex(), buffer.writeIndex());
    }

    public String toString(int index, int length, Charset charset) {
        byte[] bytes = new byte[length];
        checkIndex(this.buffer, index, length);
        this.buffer.copyTo(index, bytes, 0, length);
        return new String(bytes, charset);
    }

    public void markReaderIndex() {
        this.markedReaderIndex = readerIndex();
    }

    public void resetReaderIndex() {
        readerIndex(this.markedReaderIndex);
    }

    public void markWriterIndex() {
        this.markedWriterIndex = writerIndex();
    }

    public void resetWriterIndex() {
        writerIndex(this.markedWriterIndex);
    }

    public PacketBuffer retain() {
        REFERENCES.incrementAndGet(this);
        return this;
    }

    public boolean release() {
        return REFERENCES.decrementAndGet(this) == 0;
    }

    public int references() {
        return this.references;
    }

    private PacketBuffer copy(long index, long length, long readIndex, long writeIndex) {
        NetworkBuffer target = NetworkBuffer.resizableBuffer((int) length, this.buffer.registries());
        NetworkBuffer.copy(this.buffer, index, target, 0, length);
        target.index(readIndex, writeIndex);
        return new PacketBuffer(target);
    }

    private static void checkIndex(NetworkBuffer buffer, int index, int length) {
        if (index < 0 || index + length > buffer.writeIndex()) {
            throw new IndexOutOfBoundsException("Range [" + index + ", " + (index + length)
                    + ") exceeds the written bytes: " + buffer.writeIndex());
        }
    }
}
