package io.github.retrooper.packetevents.minestom.connection;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.UserConnectEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.PacketEventsImplHelper;
import io.github.retrooper.packetevents.minestom.handlers.PacketEventsDecoder;
import io.github.retrooper.packetevents.minestom.handlers.PacketEventsEncoder;
import io.github.retrooper.packetevents.minestom.injector.MinestomChannelInjector;
import io.github.retrooper.packetevents.minestom.netty.buffer.PacketBuffer;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.PacketVanilla;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.player.PlayerSocketConnection;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.locks.LockSupport;

public final class PacketConnection {

    private static final ThreadLocal<PacketConnection> CURRENT = new ThreadLocal<>();

    private final MinestomChannelInjector injector;
    private final PacketEventsDecoder decoder;
    private volatile @Nullable PlayerSocketConnection connection;
    private volatile @Nullable User user;
    private @Nullable PacketEventsEncoder encoder;
    private @Nullable OutboundQueue queue;
    private boolean disconnected;

    private PacketConnection(MinestomChannelInjector injector) {
        this.injector = injector;
        this.decoder = new PacketEventsDecoder(this);
    }

    public static @Nullable PacketConnection current() {
        return CURRENT.get();
    }

    public static PacketConnection current(MinestomChannelInjector injector) {
        PacketConnection current = CURRENT.get();
        if (current == null) {
            current = new PacketConnection(injector);
            CURRENT.set(current);
        }
        return current;
    }

    public boolean open(PlayerSocketConnection connection) {
        PacketEventsEncoder encoder = new PacketEventsEncoder(this, connection);
        OutboundQueue queue = new OutboundQueue(encoder);
        this.encoder = encoder;
        this.queue = queue;
        this.connection = connection;
        this.injector.internals().queue(connection, queue);

        User user = new User(this, ConnectionState.HANDSHAKING, null, new UserProfile(null, null));
        UserConnectEvent event = new UserConnectEvent(user);
        PacketEvents.getAPI().getEventManager().callEvent(event);
        if (event.isCancelled()) {
            connection.disconnect();
            return false;
        }
        PacketEvents.getAPI().getProtocolManager().setUser(this, user);
        return true;
    }

    public void send(PacketBuffer buffer, boolean silent) {
        PlayerSocketConnection connection = this.connection;
        PacketEventsEncoder encoder = this.encoder;
        OutboundQueue queue = this.queue;
        if (connection == null || encoder == null || queue == null || !connection.isOnline()) {
            buffer.release();
            return;
        }
        if (Thread.currentThread() == connection.writeThread() && encoder.encoding()) {
            encoder.write(buffer, silent);
            return;
        }
        queue.submit(new Outbound(buffer, silent));
        LockSupport.unpark(connection.writeThread());
    }

    public void receive(PacketBuffer buffer, boolean silent) {
        PlayerSocketConnection connection = this.connection;
        try {
            if (connection == null || !connection.isOnline()) {
                return;
            }
            if (!silent) {
                fire(buffer);
            }
            if (!buffer.isReadable()) {
                return;
            }
            NetworkBuffer data = buffer.buffer();
            data.registries(MinecraftServer.getRegistries());
            net.minestom.server.network.ConnectionState state = connection.getClientState();
            int id = data.read(NetworkBuffer.VAR_INT);
            dispatch(connection, state, this.injector.parser().parse(state, id, data));
        } finally {
            buffer.release();
        }
    }

    public void execute(Runnable task) {
        PlayerSocketConnection connection = this.connection;
        OutboundQueue queue = this.queue;
        if (connection == null || queue == null || Thread.currentThread() == connection.writeThread()) {
            task.run();
            return;
        }
        queue.submit(task);
        LockSupport.unpark(connection.writeThread());
    }

    public @Nullable PacketReceiveEvent fire(PacketBuffer buffer) {
        try {
            return PacketEventsImplHelper.handleServerBoundPacket(this, this.user, player(), buffer, true);
        } catch (Exception exception) {
            buffer.clear();
            handleException(exception);
            return null;
        }
    }

    public void handleException(Throwable cause) {
        PacketEventsAPI<?> api = PacketEvents.getAPI();
        User user = this.user;
        String name = user == null || user.getName() == null ? String.valueOf(remoteAddress()) : user.getName();
        if (api.getSettings().isFullStackTraceEnabled()) {
            api.getLogManager().warn("An error occurred while processing a packet of " + name, cause);
        } else {
            api.getLogManager().warn(cause.getMessage());
        }
        PlayerSocketConnection connection = this.connection;
        if (connection == null || !api.getSettings().isKickOnPacketExceptionEnabled()) {
            return;
        }
        connection.kick(Component.text("Invalid packet"));
        api.getLogManager().warn("Disconnected " + name + " due to an invalid packet!");
    }

    public void close() {
        PlayerSocketConnection connection = this.connection;
        if (connection != null) {
            connection.disconnect();
        }
    }

    public void disconnect() {
        if (this.disconnected) {
            return;
        }
        this.disconnected = true;
        User user = this.user;
        PacketEventsImplHelper.handleDisconnection(this, user == null ? null : user.getUUID());
    }

    public boolean isOpen() {
        PlayerSocketConnection connection = this.connection;
        return connection != null && connection.isOnline();
    }

    public boolean active() {
        return this.user != null && this.injector.isInjected();
    }

    public @Nullable SocketAddress remoteAddress() {
        PlayerSocketConnection connection = this.connection;
        return connection == null ? null : connection.getRemoteAddress();
    }

    public @Nullable SocketAddress localAddress() {
        PlayerSocketConnection connection = this.connection;
        if (connection == null) {
            return null;
        }
        try {
            return connection.getChannel().getLocalAddress();
        } catch (IOException exception) {
            return null;
        }
    }

    public MinestomChannelInjector injector() {
        return this.injector;
    }

    public PacketEventsDecoder decoder() {
        return this.decoder;
    }

    public @Nullable PlayerSocketConnection connection() {
        return this.connection;
    }

    public @Nullable Player player() {
        PlayerSocketConnection connection = this.connection;
        return connection == null ? null : connection.getPlayer();
    }

    public @Nullable User user() {
        return this.user;
    }

    public void user(User user) {
        this.user = user;
    }

    private static void dispatch(PlayerSocketConnection connection,
                                 net.minestom.server.network.ConnectionState state, ClientPacket packet) {
        Player player = connection.getPlayer();
        if (player == null || PacketVanilla.nextClientState(packet, state) != state) {
            MinecraftServer.getPacketListenerManager().processClientPacket(packet, connection);
            return;
        }
        player.addPacketToQueue(packet);
    }
}
