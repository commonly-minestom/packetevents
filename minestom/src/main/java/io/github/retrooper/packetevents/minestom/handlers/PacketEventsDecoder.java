package io.github.retrooper.packetevents.minestom.handlers;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import io.github.retrooper.packetevents.minestom.connection.PacketConnection;
import io.github.retrooper.packetevents.minestom.netty.buffer.PacketBuffer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.listener.manager.PacketListenerManager;
import net.minestom.server.listener.manager.PacketPrePlayListenerConsumer;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.PacketRegistry;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.handshake.ClientHandshakePacket;
import net.minestom.server.network.player.PlayerSocketConnection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PacketEventsDecoder {

    private static final int INITIAL_CAPACITY = 256;

    private final PacketConnection channel;
    private final NetworkBuffer scratch = NetworkBuffer.resizableBuffer(INITIAL_CAPACITY, MinecraftServer.getRegistries());
    private final PacketBuffer buffer = new PacketBuffer(this.scratch);
    private @Nullable List<Deferred<?>> deferred;
    private boolean opened;
    private boolean bypassed;

    public PacketEventsDecoder(PacketConnection channel) {
        this.channel = channel;
    }

    public <T> @Nullable T decode(PacketRegistry<T> registry, PacketRegistry.PacketInfo<T> info, NetworkBuffer payload) {
        if (this.opened) {
            return this.channel.active() ? process(registry, info, payload) : info.serializer().read(payload);
        }
        if (this.bypassed) {
            return info.serializer().read(payload);
        }
        return defer(registry, info, payload);
    }

    public boolean pending() {
        return this.deferred != null;
    }

    public void open(PlayerSocketConnection connection, PacketPrePlayListenerConsumer<ClientHandshakePacket> next) {
        List<Deferred<?>> deferred = this.deferred;
        if (deferred == null) {
            return;
        }
        this.deferred = null;
        this.opened = true;
        if (!this.channel.open(connection)) {
            return;
        }
        if (!(process(deferred.getFirst()) instanceof ClientHandshakePacket handshake)) {
            return;
        }
        next.accept(handshake, connection);

        PacketListenerManager manager = MinecraftServer.getPacketListenerManager();
        for (int i = 1; i < deferred.size(); i++) {
            if (process(deferred.get(i)) instanceof ClientPacket packet) {
                manager.processClientPacket(packet, connection);
            }
        }
    }

    private <T> @Nullable T defer(PacketRegistry<T> registry, PacketRegistry.PacketInfo<T> info, NetworkBuffer payload) {
        List<Deferred<?>> deferred = this.deferred;
        if (deferred != null) {
            deferred.add(new Deferred<>(registry, info, snapshot(payload)));
            return null;
        }
        if (registry.state() != ConnectionState.HANDSHAKE) {
            this.bypassed = true;
            return info.serializer().read(payload);
        }
        deferred = new ArrayList<>(2);
        deferred.add(new Deferred<>(registry, info, snapshot(payload)));
        this.deferred = deferred;
        return info.serializer().read(payload);
    }

    private <T> @Nullable T process(Deferred<T> deferred) {
        return process(deferred.registry(), deferred.info(), deferred.payload());
    }

    private <T> @Nullable T process(PacketRegistry<T> registry, PacketRegistry.PacketInfo<T> info, NetworkBuffer payload) {
        NetworkBuffer scratch = this.scratch;
        PacketBuffer buffer = this.buffer;
        scratch.clear();
        scratch.write(NetworkBuffer.VAR_INT, info.id());
        buffer.append(payload, payload.readIndex(), payload.readableBytes());

        PacketReceiveEvent event = this.channel.fire(buffer);
        if (!buffer.isReadable()) {
            return null;
        }
        if (event == null || event.getLastUsedWrapper() == null) {
            return info.serializer().read(payload);
        }
        payload.readIndex(payload.writeIndex());
        int id = scratch.read(NetworkBuffer.VAR_INT);
        return registry.packetInfo(id).serializer().read(scratch);
    }

    private static NetworkBuffer snapshot(NetworkBuffer payload) {
        long length = payload.readableBytes();
        return payload.copy(payload.readIndex(), length, 0, length);
    }

    private record Deferred<T>(PacketRegistry<T> registry, PacketRegistry.PacketInfo<T> info, NetworkBuffer payload) {
    }
}
