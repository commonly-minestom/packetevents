package io.github.retrooper.packetevents.minestom.connection;

import io.github.retrooper.packetevents.minestom.handlers.PacketEventsEncoder;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.utils.collection.ConcurrentMessageQueues;
import org.jctools.queues.MessagePassingQueue;
import org.jetbrains.annotations.Nullable;

public final class OutboundQueue implements MessagePassingQueue<SendablePacket> {

    private static final int CHUNK_SIZE = 1024;

    private final MessagePassingQueue<Object> messages = ConcurrentMessageQueues.mpscUnboundedArrayQueue(CHUNK_SIZE);
    private final PacketEventsEncoder encoder;
    private @Nullable SendablePacket head;

    public OutboundQueue(PacketEventsEncoder encoder) {
        this.encoder = encoder;
    }

    public void submit(Object message) {
        this.messages.offer(message);
    }

    @Override
    public boolean offer(SendablePacket packet) {
        return this.messages.offer(packet);
    }

    @Override
    public boolean relaxedOffer(SendablePacket packet) {
        return this.messages.relaxedOffer(packet);
    }

    @Override
    public @Nullable SendablePacket peek() {
        SendablePacket head = this.head;
        if (head != null) {
            return head;
        }
        Object message;
        while ((message = this.messages.poll()) != null) {
            head = this.encoder.encode(message);
            if (head != null) {
                this.head = head;
                return head;
            }
        }
        this.encoder.idle();
        return null;
    }

    @Override
    public @Nullable SendablePacket poll() {
        SendablePacket head = peek();
        this.head = null;
        return head;
    }

    @Override
    public @Nullable SendablePacket relaxedPeek() {
        return peek();
    }

    @Override
    public @Nullable SendablePacket relaxedPoll() {
        return poll();
    }

    @Override
    public int size() {
        return this.messages.size() + (this.head == null ? 0 : 1);
    }

    @Override
    public boolean isEmpty() {
        if (this.head != null || !this.messages.isEmpty()) {
            return false;
        }
        this.encoder.idle();
        return true;
    }

    @Override
    public void clear() {
        this.head = null;
        this.messages.clear();
    }

    @Override
    public int capacity() {
        return this.messages.capacity();
    }

    @Override
    public int drain(Consumer<SendablePacket> consumer) {
        return drain(consumer, Integer.MAX_VALUE);
    }

    @Override
    public int drain(Consumer<SendablePacket> consumer, int limit) {
        int drained = 0;
        SendablePacket packet;
        while (drained < limit && (packet = poll()) != null) {
            consumer.accept(packet);
            drained++;
        }
        return drained;
    }

    @Override
    public void drain(Consumer<SendablePacket> consumer, WaitStrategy wait, ExitCondition exit) {
        int idle = 0;
        while (exit.keepRunning()) {
            SendablePacket packet = poll();
            if (packet == null) {
                idle = wait.idle(idle);
                continue;
            }
            idle = 0;
            consumer.accept(packet);
        }
    }

    @Override
    public int fill(Supplier<SendablePacket> supplier) {
        return this.messages.fill(supplier::get);
    }

    @Override
    public int fill(Supplier<SendablePacket> supplier, int limit) {
        return this.messages.fill(supplier::get, limit);
    }

    @Override
    public void fill(Supplier<SendablePacket> supplier, WaitStrategy wait, ExitCondition exit) {
        this.messages.fill(supplier::get, wait, exit);
    }
}
