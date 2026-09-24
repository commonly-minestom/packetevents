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

import net.minestom.server.MinecraftServer;
import net.minestom.server.network.socket.Server;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// starts minestom normally but keeps its accept loop away from the real address, which packetevents listens on
final class ServerTakeover {

    private static final int MAX_UNIX_PATH_LENGTH = 100;

    private final Server server;
    private final VarHandle serverSocket;
    private final VarHandle socketAddress;
    private final VarHandle address;
    private final VarHandle port;

    // resolves every field up front so a failure happens before anything is started
    ServerTakeover(Server server) {
        this.server = server;

        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(Server.class, MethodHandles.lookup());

            this.serverSocket = lookup.findVarHandle(Server.class, "serverSocket", ServerSocketChannel.class);
            this.socketAddress = lookup.findVarHandle(Server.class, "socketAddress", SocketAddress.class);
            this.address = lookup.findVarHandle(Server.class, "address", String.class);
            this.port = lookup.findVarHandle(Server.class, "port", int.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Can't access the Minestom server socket, the package"
                    + " net.minestom.server.network.socket has to be open to packetevents", e);
        }
    }

    ServerSocketChannel start(MinecraftServer minecraftServer, SocketAddress target) throws IOException {
        Path directory = privateDirectory();
        SocketAddress decoy = decoyAddress(directory);

        try {
            // minestom's accept loop only ever reads this socket, closing it ends the loop whenever it runs
            minecraftServer.start(decoy);
            ((ServerSocketChannel) serverSocket.get(server)).close();

            ServerSocketChannel channel = open(target);

            try {
                publish(channel, target);
            } catch (IOException | RuntimeException e) {
                channel.close();
                throw e;
            }

            return channel;
        } catch (IOException | RuntimeException e) {
            // minestom already considers itself started at this point
            MinecraftServer.stopCleanly();
            throw e;
        } finally {
            deleteDecoy(directory, decoy);
        }
    }

    // what Server#init would have stored, minus the socket itself
    private void publish(ServerSocketChannel channel, SocketAddress target) throws IOException {
        String host = switch (target) {
            case InetSocketAddress inetAddress -> inetAddress.getHostString();
            case UnixDomainSocketAddress unixAddress -> "unix://" + unixAddress.getPath();
            default -> throw new IllegalArgumentException("Address must be an InetSocketAddress or a UnixDomainSocketAddress");
        };

        int boundPort = channel.getLocalAddress() instanceof InetSocketAddress bound ? bound.getPort() : 0;

        address.set(server, host);
        port.set(server, boundPort);
        socketAddress.set(server, target);
    }

    private static ServerSocketChannel open(SocketAddress target) throws IOException {
        ProtocolFamily family = switch (target) {
            case InetSocketAddress inetAddress -> inetAddress.getAddress().getAddress().length == 4
                    ? StandardProtocolFamily.INET
                    : StandardProtocolFamily.INET6;
            case UnixDomainSocketAddress _ -> StandardProtocolFamily.UNIX;
            default -> throw new IllegalArgumentException("Address must be an InetSocketAddress or a UnixDomainSocketAddress");
        };

        ServerSocketChannel channel = ServerSocketChannel.open(family);

        try {
            channel.bind(target);
        } catch (IOException e) {
            channel.close();
            throw e;
        }

        return channel;
    }

    private static @Nullable Path privateDirectory() {
        try {
            return Files.createTempDirectory("packetevents");
        } catch (IOException _) {
            return null;
        }
    }

    // a unix socket in a private directory can't be reached by anyone else, loopback is the fallback
    private static SocketAddress decoyAddress(@Nullable Path directory) {
        if (directory != null && supportsUnixSockets()) {
            Path path = directory.resolve("minestom.sock");

            if (path.toString().getBytes(StandardCharsets.UTF_8).length < MAX_UNIX_PATH_LENGTH) return UnixDomainSocketAddress.of(path);
        }

        return new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
    }

    private static boolean supportsUnixSockets() {
        try (ServerSocketChannel _ = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            return true;
        } catch (IOException | UnsupportedOperationException _) {
            return false;
        }
    }

    private static void deleteDecoy(@Nullable Path directory, SocketAddress decoy) throws IOException {
        if (decoy instanceof UnixDomainSocketAddress unixAddress) Files.deleteIfExists(unixAddress.getPath());

        if (directory != null) Files.deleteIfExists(directory);
    }
}
