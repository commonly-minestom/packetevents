package io.github.retrooper.packetevents.minestom.injector;

import io.github.retrooper.packetevents.minestom.connection.PacketConnection;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.PacketRegistry;
import net.minestom.server.registry.Registries;
import org.jetbrains.annotations.Nullable;

final class InboundType<T> implements NetworkBuffer.Type<T> {

    private final MinestomChannelInjector injector;
    private final PacketRegistry<T> registry;
    private final PacketRegistry.PacketInfo<T> info;

    InboundType(MinestomChannelInjector injector, PacketRegistry<T> registry, PacketRegistry.PacketInfo<T> info) {
        this.injector = injector;
        this.registry = registry;
        this.info = info;
    }

    @Override
    public void write(NetworkBuffer buffer, T value) {
        this.info.serializer().write(buffer, value);
    }

    @Override
    public @Nullable T read(NetworkBuffer buffer) {
        if (!this.injector.isInjected()) {
            return this.info.serializer().read(buffer);
        }
        return PacketConnection.current(this.injector).decoder().decode(this.registry, this.info, buffer);
    }

    @Override
    public long sizeOf(T value, @Nullable Registries registries) {
        return this.info.serializer().sizeOf(value, registries);
    }
}
