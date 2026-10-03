package io.github.retrooper.packetevents.minestom.handlers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.util.EventCreationUtil;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import io.github.retrooper.packetevents.minestom.connection.Outbound;
import io.github.retrooper.packetevents.minestom.connection.PacketConnection;
import io.github.retrooper.packetevents.minestom.netty.buffer.PacketBuffer;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.adventure.MinestomAdventure;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.ListenerHandle;
import net.minestom.server.event.player.PlayerPacketOutEvent;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.PacketRegistry;
import net.minestom.server.network.packet.PacketVanilla;
import net.minestom.server.network.packet.server.BufferedPacket;
import net.minestom.server.network.packet.server.CachedPacket;
import net.minestom.server.network.packet.server.FramedPacket;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.login.SetCompressionPacket;
import net.minestom.server.network.player.PlayerSocketConnection;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.zip.DataFormatException;

public final class PacketEventsEncoder {

    private static final int INITIAL_CAPACITY = 1024;
    private static final int LENGTH_BYTES = 3;
    private static final int DEFLATE_OVERHEAD = 64;

    private final PacketConnection channel;
    private final PlayerSocketConnection connection;
    private final ListenerHandle<PlayerPacketOutEvent> outgoing = EventDispatcher.getHandle(PlayerPacketOutEvent.class);
    private final NetworkBuffer scratch = NetworkBuffer.resizableBuffer(INITIAL_CAPACITY, MinecraftServer.getRegistries());
    private final PacketBuffer buffer = new PacketBuffer(this.scratch);
    private final NetworkBuffer frames = NetworkBuffer.resizableBuffer(INITIAL_CAPACITY, MinecraftServer.getRegistries());
    private boolean compression;
    private boolean encoding;

    public PacketEventsEncoder(PacketConnection channel, PlayerSocketConnection connection) {
        this.channel = channel;
        this.connection = connection;
    }

    public @Nullable SendablePacket encode(Object message) {
        if (message instanceof SendablePacket packet && !this.channel.active()) {
            this.compression |= packet instanceof SetCompressionPacket;
            return packet;
        }
        NetworkBuffer frames = this.frames;
        frames.clear();
        this.encoding = true;
        try {
            write(message);
        } finally {
            this.encoding = false;
        }
        long length = frames.writeIndex();
        return length == 0 ? null : new BufferedPacket(frames, 0, length);
    }

    public void write(PacketBuffer buffer, boolean silent) {
        try {
            emit(buffer, null, 0, 0, silent || !this.channel.active());
        } finally {
            buffer.release();
        }
    }

    public boolean encoding() {
        return this.encoding;
    }

    public void idle() {
        if (!this.connection.isOnline()) {
            this.channel.disconnect();
        }
    }

    private void write(Object message) {
        switch (message) {
            case ServerPacket packet -> write(packet, null, true);
            case FramedPacket framed -> write(framed.packet(), framed.body(), false);
            case CachedPacket cached -> {
                ConnectionState state = this.connection.getServerState();
                write(cached.packet(state), cached.body(state), false);
            }
            case BufferedPacket buffered -> write(buffered);
            case Outbound outbound -> write(outbound.buffer(), outbound.silent());
            case Runnable task -> run(task);
            default -> throw new IllegalStateException("Unsupported outbound message: " + message.getClass().getName());
        }
    }

    private void write(ServerPacket packet, @Nullable NetworkBuffer body, boolean raw) {
        PlayerSocketConnection connection = this.connection;
        ConnectionState state = connection.getServerState();
        Player player = connection.getPlayer();
        if (player != null) {
            if (this.outgoing.hasListener()) {
                PlayerPacketOutEvent event = new PlayerPacketOutEvent(player, packet);
                this.outgoing.call(event);
                if (event.isCancelled()) {
                    return;
                }
            }
            if (raw && ServerFlag.AUTOMATIC_COMPONENT_TRANSLATION
                    && packet instanceof ServerPacket.ComponentHolding holding) {
                Locale locale = player.getLocale() == null ? MinestomAdventure.getDefaultLocale() : player.getLocale();
                packet = holding.copyWithOperator(component -> translate(component, locale));
            }
        }
        if (raw) {
            ConnectionState next = PacketVanilla.nextServerState(packet, state);
            if (next != state) {
                connection.setServerState(next);
            }
        }

        NetworkBuffer scratch = this.scratch;
        PacketRegistry.PacketInfo<ServerPacket> info = info(state, packet);
        scratch.clear();
        scratch.write(NetworkBuffer.VAR_INT, info.id());
        scratch.write(info.serializer(), packet);

        PacketSendEvent event = dispatch(this.buffer, body, 0, body == null ? 0 : body.capacity(), false);
        this.compression |= raw && packet instanceof SetCompressionPacket;
        complete(event);
    }

    private void write(BufferedPacket packet) {
        boolean compressed = MinecraftServer.getCompressionThreshold() > 0;
        NetworkBuffer scratch = this.scratch;
        NetworkBuffer view = packet.buffer().slice(packet.index(), packet.length(), 0, packet.length());
        while (view.readableBytes() > 0) {
            long start = view.readIndex();
            int length = view.read(NetworkBuffer.VAR_INT);
            long end = view.readIndex() + length;
            scratch.clear();
            int inflated = compressed ? view.read(NetworkBuffer.VAR_INT) : 0;
            if (inflated == 0) {
                this.buffer.append(view, view.readIndex(), end - view.readIndex());
            } else {
                inflate(view, end, inflated);
            }
            view.readIndex(end);
            emit(this.buffer, view, start, end - start, false);
        }
    }

    private void inflate(NetworkBuffer source, long end, int length) {
        NetworkBuffer scratch = this.scratch;
        scratch.ensureWritable(length);
        try {
            source.decompress(source.readIndex(), end - source.readIndex(), scratch);
        } catch (DataFormatException exception) {
            throw new IllegalStateException("Failed to inflate an outgoing packet", exception);
        }
    }

    private void emit(PacketBuffer buffer, @Nullable NetworkBuffer framed, long index, long length, boolean silent) {
        complete(dispatch(buffer, framed, index, length, silent));
    }

    private @Nullable PacketSendEvent dispatch(PacketBuffer buffer, @Nullable NetworkBuffer framed,
                                               long index, long length, boolean silent) {
        if (!buffer.isReadable()) {
            return null;
        }
        PacketSendEvent event = silent ? null : fire(buffer);
        if (!buffer.isReadable() || event != null && event.isCancelled()) {
            return event;
        }
        if (framed != null && (event == null || event.getLastUsedWrapper() == null)) {
            append(framed, index, length);
        } else {
            frame(buffer.buffer());
        }
        return event;
    }

    private static void complete(@Nullable PacketSendEvent event) {
        if (event == null || !event.hasPostTasks()) {
            return;
        }
        for (Runnable task : event.getPostTasks()) {
            task.run();
        }
    }

    private @Nullable PacketSendEvent fire(PacketBuffer buffer) {
        try {
            int start = buffer.readerIndex();
            PacketSendEvent event = EventCreationUtil.createSendEvent(
                    this.channel, this.channel.user(), this.channel.player(), buffer, true);
            int index = buffer.readerIndex();
            PacketEvents.getAPI().getEventManager().callEvent(event, () -> buffer.readerIndex(index));
            if (event.isCancelled()) {
                return event;
            }
            PacketWrapper<?> wrapper = event.getLastUsedWrapper();
            if (wrapper == null) {
                buffer.readerIndex(start);
                return event;
            }
            buffer.clear();
            wrapper.writeVarInt(event.getPacketId());
            wrapper.write();
            return event;
        } catch (RuntimeException exception) {
            buffer.clear();
            this.channel.handleException(exception);
            return null;
        }
    }

    private void frame(NetworkBuffer packet) {
        NetworkBuffer frames = this.frames;
        long index = packet.readIndex();
        int length = (int) packet.readableBytes();
        if (!this.compression) {
            frames.write(NetworkBuffer.VAR_INT, length);
            append(packet, index, length);
            return;
        }
        if (length < MinecraftServer.getCompressionThreshold()) {
            frames.write(NetworkBuffer.VAR_INT, length + 1);
            frames.write(NetworkBuffer.VAR_INT, 0);
            append(packet, index, length);
            return;
        }
        frames.ensureWritable(LENGTH_BYTES + deflateBound(length));
        long header = frames.advanceWrite(LENGTH_BYTES);
        frames.write(NetworkBuffer.VAR_INT, length);
        packet.compress(index, length, frames);
        frames.writeAt(header, NetworkBuffer.VAR_INT_3, (int) (frames.writeIndex() - header - LENGTH_BYTES));
    }

    private void append(NetworkBuffer source, long index, long length) {
        NetworkBuffer frames = this.frames;
        frames.ensureWritable(length);
        NetworkBuffer.copy(source, index, frames, frames.writeIndex(), length);
        frames.advanceWrite(length);
    }

    private static void run(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException exception) {
            MinecraftServer.getExceptionManager().handleException(exception);
        }
    }

    private static Component translate(Component component, Locale locale) {
        return MinestomAdventure.COMPONENT_TRANSLATOR.apply(component, locale);
    }

    private static int deflateBound(int length) {
        return length + (length >> 12) + (length >> 14) + (length >> 25) + DEFLATE_OVERHEAD;
    }

    @SuppressWarnings("unchecked")
    private static PacketRegistry.PacketInfo<ServerPacket> info(ConnectionState state, ServerPacket packet) {
        PacketRegistry<ServerPacket> registry =
                (PacketRegistry<ServerPacket>) PacketVanilla.SERVER_PACKET_PARSER.stateRegistry(state);
        return registry.packetInfo(packet);
    }
}
