package io.github.retrooper.packetevents.minestom.injector;

import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.protocol.player.User;
import io.github.retrooper.packetevents.minestom.connection.PacketConnection;
import net.minestom.server.MinecraftServer;
import net.minestom.server.listener.manager.PacketListenerManager;
import net.minestom.server.listener.manager.PacketPrePlayListenerConsumer;
import net.minestom.server.listener.preplay.HandshakeListener;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.PacketParser;
import net.minestom.server.network.packet.PacketVanilla;
import net.minestom.server.network.packet.client.handshake.ClientHandshakePacket;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.network.player.PlayerSocketConnection;
import net.minestom.server.network.socket.Server;

public final class MinestomChannelInjector implements ChannelInjector {

    private final MinestomInternals internals = new MinestomInternals();
    private PacketParser.Client parser = PacketVanilla.CLIENT_PACKET_PARSER;
    private PacketPrePlayListenerConsumer<ClientHandshakePacket> handshake = HandshakeListener::listener;
    private volatile boolean injected;

    @Override
    public void inject() {
        if (this.injected) {
            return;
        }
        Server server = MinecraftServer.getServer();
        PacketListenerManager manager = MinecraftServer.getPacketListenerManager();
        PacketParser.Client parser = server.packetParser();
        PacketPrePlayListenerConsumer<ClientHandshakePacket> handshake = this.internals.handshakeListener(manager);

        this.parser = parser;
        if (handshake != null) {
            this.handshake = handshake;
        }
        this.internals.parser(server, new PacketParser.Client(
                new InboundRegistry<>(this, parser.handshake()),
                new InboundRegistry<>(this, parser.status()),
                new InboundRegistry<>(this, parser.login()),
                new InboundRegistry<>(this, parser.configuration()),
                new InboundRegistry<>(this, parser.play())));
        manager.setListener(ConnectionState.HANDSHAKE, ClientHandshakePacket.class, this::handshake);
        this.injected = true;
    }

    @Override
    public void uninject() {
        if (!this.injected) {
            return;
        }
        this.injected = false;
        this.internals.parser(MinecraftServer.getServer(), this.parser);
        MinecraftServer.getPacketListenerManager()
                .setListener(ConnectionState.HANDSHAKE, ClientHandshakePacket.class, this.handshake);
    }

    @Override
    public void updateUser(Object channel, User user) {
        ((PacketConnection) channel).user(user);
    }

    @Override
    public void setPlayer(Object channel, Object player) {
    }

    @Override
    public boolean isPlayerSet(Object channel) {
        return ((PacketConnection) channel).player() != null;
    }

    @Override
    public boolean isProxy() {
        return false;
    }

    public boolean isInjected() {
        return this.injected;
    }

    public PacketParser.Client parser() {
        return this.parser;
    }

    public MinestomInternals internals() {
        return this.internals;
    }

    private void handshake(ClientHandshakePacket packet, PlayerConnection connection) {
        PacketConnection channel = PacketConnection.current();
        if (channel != null && channel.decoder().pending()
                && connection instanceof PlayerSocketConnection socket
                && socket.readThread() == Thread.currentThread()) {
            channel.decoder().open(socket, this.handshake);
            return;
        }
        this.handshake.accept(packet, connection);
    }
}
