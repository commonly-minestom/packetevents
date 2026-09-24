/*
 * This file is part of packetevents - https://github.com/retrooper/packetevents
 * Copyright (C) 2026 retrooper and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.github.retrooper.packetevents.minestom.factory;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import com.github.retrooper.packetevents.util.PEVersions;
import io.github.retrooper.packetevents.minestom.InternalMinestomListener;
import io.github.retrooper.packetevents.minestom.injector.MinestomChannelInjector;
import io.github.retrooper.packetevents.minestom.manager.MinestomPlayerManager;
import io.github.retrooper.packetevents.minestom.manager.MinestomProtocolManager;
import io.github.retrooper.packetevents.minestom.manager.MinestomServerManager;
import io.github.retrooper.packetevents.minestom.netty.MinestomNettyManager;
import io.github.retrooper.packetevents.minestom.network.MinestomNetworkServer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerProcess;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public final class MinestomPacketEventsAPI extends PacketEventsAPI<ServerProcess> {

    private static final String ID = "minestom";

    private final ServerProcess process;
    private final PacketEventsSettings settings;
    private final ProtocolManager protocolManager = new MinestomProtocolManager();
    private final ServerManager serverManager = new MinestomServerManager();
    private final PlayerManager playerManager = new MinestomPlayerManager();
    private final NettyManager nettyManager = new MinestomNettyManager();
    private final MinestomNetworkServer network = new MinestomNetworkServer(this);
    private final MinestomChannelInjector injector = new MinestomChannelInjector(network);
    private final InternalMinestomListener listener = new InternalMinestomListener(this);

    private boolean loaded;
    private boolean initialized;
    private boolean terminated;

    MinestomPacketEventsAPI(ServerProcess process, PacketEventsSettings settings) {
        this.process = process;
        this.settings = settings;
    }

    /**
     * Starts the Minestom server with packetevents handling its connections,
     * use it instead of {@link MinecraftServer#start(SocketAddress)}
     */
    public void start(MinecraftServer minecraftServer, SocketAddress address) {
        init();

        if (PacketEvents.getAPI() != this) throw new IllegalStateException("PacketEvents#setAPI must be called with this instance first");

        try {
            network.start(minecraftServer, address);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start the Minestom server", e);
        }
    }

    /**
     * Starts the Minestom server with packetevents handling its connections,
     * use it instead of {@link MinecraftServer#start(String, int)}
     */
    public void start(MinecraftServer minecraftServer, String address, int port) {
        start(minecraftServer, new InetSocketAddress(address, port));
    }

    @Override
    public void load() {
        if (loaded) return;

        PacketEvents.IDENTIFIER = "pe-" + ID;
        PacketEvents.ENCODER_NAME = "pe-encoder-" + ID;
        PacketEvents.DECODER_NAME = "pe-decoder-" + ID;
        PacketEvents.CONNECTION_HANDLER_NAME = "pe-connection-handler-" + ID;
        PacketEvents.SERVER_CHANNEL_HANDLER_NAME = "pe-connection-initializer-" + ID;
        PacketEvents.TIMEOUT_HANDLER_NAME = "pe-timeout-handler-" + ID;

        super.load();
        injector.inject();
        loaded = true;

        getLogManager().info("Loaded packetevents v" + PEVersions.RAW + " for Minestom");
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    @Override
    public void init() {
        load();
        if (initialized) return;

        if (settings.shouldCheckForUpdates()) getUpdateChecker().handleUpdateCheck();

        PacketType.Play.Client.load();
        PacketType.Play.Server.load();

        process.eventHandler().addChild(listener.node());
        process.scheduler().scheduleNextTick(this::checkStarted);
        initialized = true;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void terminate() {
        if (!initialized) return;

        super.terminate();
        process.eventHandler().removeChild(listener.node());
        initialized = false;
        terminated = true;
    }

    @Override
    public boolean isTerminated() {
        return terminated;
    }

    @Override
    public ServerProcess getPlugin() {
        return process;
    }

    @Override
    public ServerManager getServerManager() {
        return serverManager;
    }

    @Override
    public ProtocolManager getProtocolManager() {
        return protocolManager;
    }

    @Override
    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    @Override
    public NettyManager getNettyManager() {
        return nettyManager;
    }

    @Override
    public ChannelInjector getInjector() {
        return injector;
    }

    @Override
    public PacketEventsSettings getSettings() {
        return settings;
    }

    private void checkStarted() {
        if (network.isStarted()) return;

        getLogManager().severe("The server was started without packetevents, connections can't be handled."
                + " Start it through MinestomPacketEventsAPI#start instead of MinecraftServer#start");
    }
}
