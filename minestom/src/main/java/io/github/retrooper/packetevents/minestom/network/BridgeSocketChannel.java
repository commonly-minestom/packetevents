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

import org.jetbrains.annotations.UnknownNullability;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.Set;

// the channel minestom reads and writes, plaintext uncompressed frames flow through it
final class BridgeSocketChannel extends SocketChannel {

    private final SocketChannel socket;
    private final InboundPipe pipe;

    private @UnknownNullability PacketEncoder encoder;

    BridgeSocketChannel(SocketChannel socket, InboundPipe pipe) {
        super(socket.provider());
        this.socket = socket;
        this.pipe = pipe;
    }

    void attach(PacketEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public int read(ByteBuffer destination) throws IOException {
        if (!isOpen()) throw new ClosedChannelException();

        boolean completed = false;
        begin();

        try {
            int read = pipe.read(destination);
            completed = true;

            return read;
        } catch (InterruptedException e) {
            // end() turns the interrupt into a ClosedByInterruptException
            Thread.currentThread().interrupt();

            return 0;
        } finally {
            end(completed);
        }
    }

    @Override
    public long read(ByteBuffer[] destinations, int offset, int length) throws IOException {
        for (int i = offset; i < offset + length; i++) {
            if (destinations[i].hasRemaining()) return read(destinations[i]);
        }

        return 0;
    }

    @Override
    public int write(ByteBuffer source) throws IOException {
        if (!isOpen()) throw new ClosedChannelException();

        return encoder.write(source);
    }

    @Override
    public long write(ByteBuffer[] sources, int offset, int length) throws IOException {
        long written = 0;

        for (int i = offset; i < offset + length; i++) written += write(sources[i]);

        return written;
    }

    @Override
    public SocketChannel bind(SocketAddress local) throws IOException {
        socket.bind(local);

        return this;
    }

    @Override
    public <T> SocketChannel setOption(SocketOption<T> name, T value) throws IOException {
        socket.setOption(name, value);

        return this;
    }

    @Override
    public <T> T getOption(SocketOption<T> name) throws IOException {
        return socket.getOption(name);
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return socket.supportedOptions();
    }

    @Override
    public SocketChannel shutdownInput() throws IOException {
        socket.shutdownInput();

        return this;
    }

    @Override
    public SocketChannel shutdownOutput() throws IOException {
        socket.shutdownOutput();

        return this;
    }

    @Override
    public Socket socket() {
        return socket.socket();
    }

    @Override
    public boolean isConnected() {
        return socket.isConnected();
    }

    @Override
    public boolean isConnectionPending() {
        return socket.isConnectionPending();
    }

    @Override
    public boolean connect(SocketAddress remote) throws IOException {
        return socket.connect(remote);
    }

    @Override
    public boolean finishConnect() throws IOException {
        return socket.finishConnect();
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        return socket.getRemoteAddress();
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return socket.getLocalAddress();
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
        pipe.close(null);
        socket.close();
    }

    @Override
    protected void implConfigureBlocking(boolean block) {
        if (!block) throw new UnsupportedOperationException("Minestom connections are always blocking");
    }
}
