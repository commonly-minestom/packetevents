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

package io.github.retrooper.packetevents.minestom.network;

import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.UserConnectEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.ServerProcess;
import net.minestom.server.network.packet.PacketParser;
import net.minestom.server.network.socket.Server;
import org.jetbrains.annotations.ApiStatus;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

// replaces minestom's accept loop so every connection is a PacketEventsPlayerConnection
@ApiStatus.Internal
public final class MinestomNetworkServer {

    private final PacketEventsAPI<?> api;
    private final ExecutorService taskExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private volatile boolean started;
    private volatile boolean eventsEnabled = true;
    private volatile int compressionThreshold;

    public MinestomNetworkServer(PacketEventsAPI<?> api) {
        this.api = api;
    }

    public synchronized void start(MinecraftServer minecraftServer, SocketAddress address) throws IOException {
        if (started) throw new IllegalStateException("packetevents already started the server");

        ServerProcess process = MinecraftServer.process();
        if (process.isAlive()) throw new IllegalStateException("The server was already started without packetevents");

        Server server = process.server();
        ServerTakeover takeover = new ServerTakeover(server);

        // minestom frames everything uncompressed, compression happens per connection in the encoder
        compressionThreshold = MinecraftServer.getCompressionThreshold();
        MinecraftServer.setCompressionThreshold(0);

        ServerSocketChannel channel = takeover.start(minecraftServer, address);
        started = true;

        // minestom only closes the socket its own accept loop used
        process.scheduler().buildShutdownTask(() -> closeQuietly(channel));
        Thread.ofVirtual().name("Ms-Socket-Server").start(() -> acceptLoop(server, channel));
    }

    public boolean isStarted() {
        return started;
    }

    public void disableEvents() {
        eventsEnabled = false;
    }

    boolean isEventsEnabled() {
        return eventsEnabled;
    }

    int compressionThreshold() {
        return compressionThreshold;
    }

    Executor taskExecutor() {
        return taskExecutor;
    }

    private void acceptLoop(Server server, ServerSocketChannel channel) {
        Thread.Builder readBuilder = Thread.ofVirtual().name("Ms-Socket-Reader-", 0);
        Thread.Builder writeBuilder = Thread.ofVirtual().name("Ms-Socket-Writer-", 0);
        Thread.Builder decodeBuilder = Thread.ofVirtual().name("Ms-Socket-Decoder-", 0);

        try {
            while (server.isOpen()) {
                SocketChannel client;

                try {
                    client = channel.accept();
                } catch (ClosedChannelException _) {
                    return;
                } catch (IOException e) {
                    MinecraftServer.getExceptionManager().handleException(e);
                    continue;
                }

                try {
                    openConnection(server, client, readBuilder, writeBuilder, decodeBuilder);
                } catch (Throwable t) {
                    closeQuietly(client);
                    MinecraftServer.getExceptionManager().handleException(t);
                }
            }
        } finally {
            taskExecutor.shutdown();
        }
    }

    private void openConnection(Server server, SocketChannel client, Thread.Builder readBuilder,
                                Thread.Builder writeBuilder, Thread.Builder decodeBuilder) throws IOException {
        configureSocket(client);

        AtomicReference<PacketEventsPlayerConnection> reference = new AtomicReference<>();
        Thread readThread = readBuilder.unstarted(() -> readLoop(server, reference.get()));
        Thread writeThread = writeBuilder.unstarted(() -> writeLoop(server, reference.get()));
        Thread decodeThread = decodeBuilder.unstarted(() -> reference.get().decoder().run());

        PacketEventsPlayerConnection connection = new PacketEventsPlayerConnection(this, client,
                client.getRemoteAddress(), readThread, writeThread, decodeThread);
        reference.set(connection);

        User user = new User(connection, ConnectionState.HANDSHAKING, null, new UserProfile(null, null));
        connection.setUser(user);

        if (eventsEnabled) {
            UserConnectEvent event = new UserConnectEvent(user);
            api.getEventManager().callEvent(event);

            if (event.isCancelled()) {
                connection.refuse();

                return;
            }
        }

        api.getProtocolManager().setUser(connection, user);

        decodeThread.start();
        readThread.start();
        writeThread.start();
    }

    private static void readLoop(Server server, PacketEventsPlayerConnection connection) {
        PacketParser.Client parser = server.packetParser();

        while (server.isOpen()) {
            try {
                connection.read(parser);
            } catch (ClosedChannelException | EOFException _) {
                connection.disconnect();

                return;
            } catch (Throwable t) {
                if (!isConnectionReset(t)) MinecraftServer.getExceptionManager().handleException(t);

                connection.disconnect();

                return;
            }
        }
    }

    private static void writeLoop(Server server, PacketEventsPlayerConnection connection) {
        try {
            while (server.isOpen()) {
                try {
                    connection.flushSync();
                } catch (ClosedChannelException | EOFException _) {
                    connection.disconnect();
                } catch (Throwable t) {
                    if (!isBrokenPipe(t)) MinecraftServer.getExceptionManager().handleException(t);

                    connection.disconnect();
                }

                if (connection.isOnline()) continue;

                try {
                    connection.flushSync();
                } catch (IOException _) {
                    // the peer is gone, nothing left to flush
                } catch (Throwable t) {
                    MinecraftServer.getExceptionManager().handleException(t);
                }

                return;
            }
        } finally {
            connection.cleanup();
            connection.release();
        }
    }

    private static void configureSocket(SocketChannel client) throws IOException {
        if (!(client.getLocalAddress() instanceof InetSocketAddress)) return;

        Socket socket = client.socket();
        socket.setSendBufferSize(ServerFlag.SOCKET_SEND_BUFFER_SIZE);
        socket.setReceiveBufferSize(ServerFlag.SOCKET_RECEIVE_BUFFER_SIZE);
        socket.setTcpNoDelay(ServerFlag.SOCKET_NO_DELAY);
        socket.setSoTimeout(ServerFlag.SOCKET_TIMEOUT);
    }

    private static boolean isConnectionReset(Throwable t) {
        return t instanceof SocketException && "Connection reset".equals(t.getMessage());
    }

    private static boolean isBrokenPipe(Throwable t) {
        return t instanceof IOException && "Broken pipe".equals(t.getMessage());
    }

    private static void closeQuietly(Channel channel) {
        try {
            channel.close();
        } catch (IOException _) {
            // nothing else to do with a broken socket
        }
    }
}
