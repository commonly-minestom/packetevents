package io.github.retrooper.packetevents.minestom.injector;

import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.PacketRegistry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class InboundRegistry<T> implements PacketRegistry<T> {

    private final PacketRegistry<T> registry;
    private final List<PacketInfo<T>> packets;

    InboundRegistry(MinestomChannelInjector injector, PacketRegistry<T> registry) {
        List<PacketInfo<T>> packets = new ArrayList<>();
        for (PacketInfo<? extends T> entry : registry) {
            PacketInfo<T> info = registry.packetInfo(entry.id());
            packets.add(new PacketInfo<>(info.packetClass(), info.id(), new InboundType<>(injector, registry, info)));
        }
        this.registry = registry;
        this.packets = List.copyOf(packets);
    }

    PacketRegistry<T> registry() {
        return this.registry;
    }

    @Override
    public T create(int packetId, NetworkBuffer reader) {
        return packetInfo(packetId).serializer().read(reader);
    }

    @Override
    public PacketInfo<T> packetInfo(Class<?> packetClass) {
        return this.registry.packetInfo(packetClass);
    }

    @Override
    public PacketInfo<T> packetInfo(int packetId) {
        if (packetId < 0 || packetId >= this.packets.size()) {
            return this.registry.packetInfo(packetId);
        }
        return this.packets.get(packetId);
    }

    @Override
    public ConnectionState state() {
        return this.registry.state();
    }

    @Override
    public ConnectionSide side() {
        return this.registry.side();
    }

    @Override
    public Iterator<PacketInfo<? extends T>> iterator() {
        return this.registry.iterator();
    }
}
