package io.github.retrooper.packetevents.minestom.factory;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.UserLoginEvent;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import com.github.retrooper.packetevents.util.PEVersions;
import io.github.retrooper.packetevents.minestom.injector.MinestomChannelInjector;
import io.github.retrooper.packetevents.minestom.manager.player.PlayerManagerImpl;
import io.github.retrooper.packetevents.minestom.manager.protocol.ProtocolManagerImpl;
import io.github.retrooper.packetevents.minestom.manager.server.ServerManagerImpl;
import io.github.retrooper.packetevents.minestom.netty.NettyManagerImpl;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerProcess;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.network.player.PlayerSocketConnection;

public final class MinestomPacketEventsBuilder {

    private static final String IDENTIFIER = "minestom";

    private static PacketEventsAPI<ServerProcess> instance;

    private MinestomPacketEventsBuilder() {
    }

    public static void clearBuildCache() {
        instance = null;
    }

    public static PacketEventsAPI<ServerProcess> build() {
        return build(new PacketEventsSettings());
    }

    public static PacketEventsAPI<ServerProcess> build(PacketEventsSettings settings) {
        if (instance == null) {
            instance = buildNoCache(settings);
        }
        return instance;
    }

    public static PacketEventsAPI<ServerProcess> buildNoCache() {
        return buildNoCache(new PacketEventsSettings());
    }

    public static PacketEventsAPI<ServerProcess> buildNoCache(PacketEventsSettings settings) {
        ServerProcess process = MinecraftServer.process();
        if (process == null) {
            throw new IllegalStateException("MinecraftServer.init() must be called before building PacketEvents");
        }
        return new MinestomPacketEvents(process, settings);
    }

    private static final class MinestomPacketEvents extends PacketEventsAPI<ServerProcess> {

        private final ServerProcess process;
        private final PacketEventsSettings settings;
        private final ProtocolManager protocolManager = new ProtocolManagerImpl();
        private final ServerManager serverManager = new ServerManagerImpl();
        private final PlayerManager playerManager = new PlayerManagerImpl();
        private final NettyManager nettyManager = new NettyManagerImpl();
        private final ChannelInjector injector = new MinestomChannelInjector();
        private final EventNode<Event> events = EventNode.all("packetevents");
        private boolean loaded;
        private boolean initialized;
        private boolean terminated;

        private MinestomPacketEvents(ServerProcess process, PacketEventsSettings settings) {
            this.process = process;
            this.settings = settings;
        }

        @Override
        public void load() {
            if (this.loaded) {
                return;
            }
            PacketEvents.IDENTIFIER = "pe-" + IDENTIFIER;
            PacketEvents.ENCODER_NAME = "pe-encoder-" + IDENTIFIER;
            PacketEvents.DECODER_NAME = "pe-decoder-" + IDENTIFIER;
            PacketEvents.CONNECTION_HANDLER_NAME = "pe-connection-handler-" + IDENTIFIER;
            PacketEvents.SERVER_CHANNEL_HANDLER_NAME = "pe-connection-initializer-" + IDENTIFIER;
            PacketEvents.TIMEOUT_HANDLER_NAME = "pe-timeout-handler-" + IDENTIFIER;

            super.load();
            this.injector.inject();
            this.loaded = true;
            getLogManager().info("Loaded packetevents v" + PEVersions.RAW + " for Minestom");
        }

        @Override
        public boolean isLoaded() {
            return this.loaded;
        }

        @Override
        public void init() {
            load();
            if (this.initialized) {
                return;
            }
            this.events.addListener(PlayerSpawnEvent.class, this::handleSpawn);
            this.process.eventHandler().addChild(this.events);
            if (this.settings.shouldCheckForUpdates()) {
                getUpdateChecker().handleUpdateCheck();
            }
            PacketType.Play.Client.load();
            PacketType.Play.Server.load();
            this.initialized = true;
        }

        @Override
        public boolean isInitialized() {
            return this.initialized;
        }

        @Override
        public void terminate() {
            if (!this.initialized) {
                return;
            }
            super.terminate();
            this.process.eventHandler().removeChild(this.events);
            this.initialized = false;
            this.terminated = true;
        }

        @Override
        public boolean isTerminated() {
            return this.terminated;
        }

        @Override
        public ServerProcess getPlugin() {
            return this.process;
        }

        @Override
        public PacketEventsSettings getSettings() {
            return this.settings;
        }

        @Override
        public ServerManager getServerManager() {
            return this.serverManager;
        }

        @Override
        public ProtocolManager getProtocolManager() {
            return this.protocolManager;
        }

        @Override
        public PlayerManager getPlayerManager() {
            return this.playerManager;
        }

        @Override
        public NettyManager getNettyManager() {
            return this.nettyManager;
        }

        @Override
        public ChannelInjector getInjector() {
            return this.injector;
        }

        private void handleSpawn(PlayerSpawnEvent event) {
            if (!event.isFirstSpawn()) {
                return;
            }
            Player player = event.getPlayer();
            User user = this.playerManager.getUser(player);
            if (user != null) {
                getEventManager().callEvent(new UserLoginEvent(user, player));
                return;
            }
            if (player.getPlayerConnection() instanceof PlayerSocketConnection) {
                player.kick(Component.text("PacketEvents failed to inject into a channel."));
            }
        }
    }
}
