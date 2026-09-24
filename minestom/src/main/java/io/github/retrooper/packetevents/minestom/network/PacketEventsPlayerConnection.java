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

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.exception.InvalidDisconnectPacketSend;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import com.github.retrooper.packetevents.util.ExceptionUtil;
import com.github.retrooper.packetevents.util.PacketEventsImplHelper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.minestom.server.ServerFlag;
import net.minestom.server.extras.mojangAuth.MojangCrypt;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.server.BufferedPacket;
import net.minestom.server.network.player.PlayerSocketConnection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Minestom connection owned by packetevents, it is also the channel object of its {@link User}
 */
public final class PacketEventsPlayerConnection extends PlayerSocketConnection {

    private final MinestomNetworkServer server;
    private final SocketChannel socket;
    private final BridgeSocketChannel bridge;
    private final InboundPipe pipe;
    private final Thread decodeThread;
    private final PacketDecoder decoder;
    private final PacketEncoder encoder;

    // serializes packetevents work of one connection, the netty event loop guarantee listeners rely on
    private final ReentrantLock eventLock = new ReentrantLock();
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean draining = new AtomicBoolean();
    private final CompletableFuture<Void> closeFuture = new CompletableFuture<>();

    private volatile @UnknownNullability User user;
    private volatile @Nullable Object packetEventsPlayer;

    PacketEventsPlayerConnection(MinestomNetworkServer server, SocketChannel socket, SocketAddress remoteAddress,
                                 Thread readThread, Thread writeThread, Thread decodeThread) {
        InboundPipe pipe = new InboundPipe();
        BridgeSocketChannel bridge = new BridgeSocketChannel(socket, pipe);

        super(bridge, remoteAddress, readThread, writeThread);

        this.server = server;
        this.socket = socket;
        this.bridge = bridge;
        this.pipe = pipe;
        this.decodeThread = decodeThread;
        this.decoder = new PacketDecoder(this, socket, pipe);
        this.encoder = new PacketEncoder(this, socket, server.compressionThreshold());

        bridge.attach(encoder);
    }

    @Override
    public void setEncryptionKey(SecretKey secretKey) {
        if (encoder.isEncryptionEnabled()) throw new IllegalStateException("Encryption is already enabled!");

        encoder.enableEncryption(MojangCrypt.getCipher(Cipher.ENCRYPT_MODE, secretKey));
        decoder.enableDecryption(MojangCrypt.getCipher(Cipher.DECRYPT_MODE, secretKey));
    }

    @Override
    public void startCompression() {
        throw new UnsupportedOperationException("Compression is handled by packetevents");
    }

    public User getUser() {
        return user;
    }

    @ApiStatus.Internal
    public void setUser(User user) {
        this.user = user;
    }

    public @Nullable Object getPacketEventsPlayer() {
        Object player = packetEventsPlayer;

        return player != null ? player : getPlayer();
    }

    @ApiStatus.Internal
    public void setPacketEventsPlayer(@Nullable Object player) {
        this.packetEventsPlayer = player;
    }

    public boolean isOpen() {
        return isOnline() && socket.isOpen();
    }

    public @Nullable SocketAddress getLocalAddress() {
        try {
            return socket.getLocalAddress();
        } catch (IOException _) {
            return null;
        }
    }

    public CompletableFuture<Void> closeFuture() {
        return closeFuture;
    }

    // takes ownership of the buffer, which holds the packet id followed by its data
    @ApiStatus.Internal
    public void writePacket(ByteBuf buffer, boolean silent) {
        if (Thread.currentThread() == writeThread() && encoder.isEncoding()) {
            try {
                encoder.encodeNested(buffer, silent);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            return;
        }

        try {
            sendPacket(frame(buffer, silent));
        } finally {
            buffer.release();
        }
    }

    // takes ownership of the buffer, which holds the packet id followed by its data
    @ApiStatus.Internal
    public void receivePacket(ByteBuf buffer, boolean silent) {
        if (Thread.currentThread() == decodeThread && decoder.isDecoding()) {
            decoder.decodeNested(buffer, silent);

            return;
        }

        lockEvents();

        try {
            if (!silent) handleInbound(buffer);
            if (!buffer.isReadable()) return;

            ByteBuf frame = Unpooled.buffer(buffer.readableBytes() + 5);
            PacketDecoder.writeFrame(frame, buffer);
            pipe.offer(frame);
        } finally {
            unlockEvents();
            buffer.release();
        }
    }

    @ApiStatus.Internal
    public void execute(Runnable task) {
        tasks.add(task);

        if (!draining.compareAndSet(false, true)) return;

        try {
            server.taskExecutor().execute(this::drainTasks);
        } catch (RejectedExecutionException _) {
            drainTasks();
        }
    }

    @ApiStatus.Internal
    public @Nullable Object handler(String name) {
        if (name.equals(PacketEvents.DECODER_NAME)) return decoder;

        return name.equals(PacketEvents.ENCODER_NAME) ? encoder : null;
    }

    void lockEvents() {
        eventLock.lock();
    }

    void unlockEvents() {
        eventLock.unlock();
    }

    PacketDecoder decoder() {
        return decoder;
    }

    Thread decodeThread() {
        return decodeThread;
    }

    void handleInbound(ByteBuf packet) {
        if (!server.isEventsEnabled()) return;

        try {
            PacketEventsImplHelper.handleServerBoundPacket(this, user, getPacketEventsPlayer(), packet, true);
        } catch (Throwable t) {
            packet.clear();
            handlePacketException(t, true);
        }
    }

    void handleOutbound(ByteBuf packet, List<Runnable> afterSend) {
        if (!server.isEventsEnabled()) return;

        int start = packet.readerIndex();

        try {
            PacketSendEvent event = PacketEventsImplHelper.handleClientBoundPacket(this, user, getPacketEventsPlayer(), packet, true);
            if (event != null && event.hasTasksAfterSend()) afterSend.addAll(event.getTasksAfterSend());
        } catch (Throwable t) {
            // minestom picks disconnect packets by state itself, let them through untouched
            if (ExceptionUtil.isException(t, InvalidDisconnectPacketSend.class)) {
                packet.readerIndex(start);

                return;
            }

            packet.clear();
            handlePacketException(t, false);
        }
    }

    void runLocked(List<Runnable> runnables) {
        lockEvents();

        try {
            for (Runnable runnable : runnables) runSafely(runnable);
        } finally {
            unlockEvents();
        }
    }

    // the connection never started, a cancelled UserConnectEvent closes it before any packet
    void refuse() throws IOException {
        bridge.close();
        closeFuture.complete(null);
    }

    // called once the write loop is done with this connection
    void release() {
        try {
            bridge.close();
        } catch (IOException _) {
            // already closed by the peer
        }

        User current = user;
        PacketEventsImplHelper.handleDisconnection(this, current.getUUID());
        closeFuture.complete(null);
    }

    private void drainTasks() {
        do {
            lockEvents();

            try {
                Runnable task;

                while ((task = tasks.poll()) != null) runSafely(task);
            } finally {
                unlockEvents();
            }

            draining.set(false);
        } while (!tasks.isEmpty() && draining.compareAndSet(false, true));
    }

    private void handlePacketException(Throwable cause, boolean inbound) {
        PacketEventsAPI<?> api = PacketEvents.getAPI();
        PacketEventsSettings settings = api.getSettings();
        User current = user;
        ConnectionState state = inbound ? current.getDecoderState() : current.getEncoderState();
        String name = current.getProfile().getName();

        if (settings.isDebugEnabled() || state != ConnectionState.HANDSHAKING) {
            if (settings.isFullStackTraceEnabled()) {
                ClientVersion clientVersion = current.getClientVersion();

                api.getLogManager().warn("An error occurred while processing a packet from " + name
                        + " (state: " + state.name()
                        + ", clientVersion: " + (clientVersion != null ? clientVersion.getReleaseName() : "null")
                        + ", serverVersion: " + api.getServerManager().getVersion().getReleaseName() + ")", cause);
            } else {
                api.getLogManager().warn(String.valueOf(cause.getMessage()));
            }
        }

        if (!settings.isKickOnPacketExceptionEnabled() || !isOnline()) return;

        // handshake and status have no disconnect packet
        boolean hasDisconnectPacket = switch (getServerState()) {
            case HANDSHAKE, STATUS -> false;
            default -> true;
        };

        if (hasDisconnectPacket) kick(Component.text("Invalid packet"));
        else disconnect();

        if (name != null) api.getLogManager().warn("Disconnected " + name + " due to an invalid packet!");
    }

    private static void runSafely(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            PacketEvents.getAPI().getLogManager().warn("A packetevents task threw an exception", t);
        }
    }

    // wraps the packet into a frame minestom writes as is, silent packets carry a marker id first
    private static BufferedPacket frame(ByteBuf buffer, boolean silent) {
        ByteBuf frame = Unpooled.buffer(buffer.readableBytes() + 8);
        frame.writerIndex(3);

        if (silent) VarInts.write(frame, PacketEncoder.SILENT_MARKER);

        frame.writeBytes(buffer, buffer.readerIndex(), buffer.readableBytes());

        int length = frame.writerIndex() - 3;
        boolean tooLarge = length > VarInts.MAX_FIXED_3 || frame.writerIndex() > ServerFlag.MAX_PACKET_SIZE;

        // minestom would keep an unwritable packet at the head of its queue forever
        if (tooLarge) throw new IllegalArgumentException("Packet is too large to be sent (" + length + " bytes)");

        VarInts.setFixed3(frame, 0, length);
        NetworkBuffer networkBuffer = NetworkBuffer.wrap(frame.array(), frame.arrayOffset(), frame.arrayOffset() + frame.writerIndex());

        return new BufferedPacket(networkBuffer, frame.arrayOffset(), frame.writerIndex());
    }
}
