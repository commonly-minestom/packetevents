package io.github.retrooper.packetevents.minestom.injector;

import net.minestom.server.listener.manager.PacketListenerManager;
import net.minestom.server.listener.manager.PacketPrePlayListenerConsumer;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.PacketParser;
import net.minestom.server.network.packet.client.handshake.ClientHandshakePacket;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.player.PlayerSocketConnection;
import net.minestom.server.network.socket.Server;
import org.jctools.queues.MessagePassingQueue;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Map;

public final class MinestomInternals {

    private final Field parser = field(Server.class, "packetParser");
    private final Field queue = field(PlayerSocketConnection.class, "packetQueue");
    private final Field listeners = field(PacketListenerManager.class, "listeners");

    public void parser(Server server, PacketParser.Client parser) {
        set(this.parser, server, parser);
    }

    public void queue(PlayerSocketConnection connection, MessagePassingQueue<SendablePacket> queue) {
        set(this.queue, connection, queue);
    }

    @SuppressWarnings("unchecked")
    public @Nullable PacketPrePlayListenerConsumer<ClientHandshakePacket> handshakeListener(PacketListenerManager manager) {
        try {
            Map<Class<?>, PacketPrePlayListenerConsumer<?>>[] listeners =
                    (Map<Class<?>, PacketPrePlayListenerConsumer<?>>[]) this.listeners.get(manager);
            return (PacketPrePlayListenerConsumer<ClientHandshakePacket>)
                    listeners[ConnectionState.HANDSHAKE.ordinal()].get(ClientHandshakePacket.class);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to read the Minestom handshake listener", exception);
        }
    }

    private static void set(Field field, Object target, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to replace " + field.getDeclaringClass().getSimpleName()
                    + "#" + field.getName(), exception);
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            throw new IllegalStateException("Unsupported Minestom version, missing " + owner.getSimpleName()
                    + "#" + name, exception);
        }
    }
}
