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

package io.github.retrooper.packetevents.minestom.injector;

import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.protocol.player.User;
import io.github.retrooper.packetevents.minestom.network.MinestomNetworkServer;
import io.github.retrooper.packetevents.minestom.network.PacketEventsPlayerConnection;

public final class MinestomChannelInjector implements ChannelInjector {

    private final MinestomNetworkServer network;

    public MinestomChannelInjector(MinestomNetworkServer network) {
        this.network = network;
    }

    @Override
    public boolean isServerBound() {
        return network.isStarted();
    }

    // connections are created by packetevents itself once it starts the server
    @Override
    public void inject() {
    }

    // packetevents owns the sockets, so they keep working and only stop calling listeners
    @Override
    public void uninject() {
        network.disableEvents();
    }

    @Override
    public void updateUser(Object channel, User user) {
        if (channel instanceof PacketEventsPlayerConnection connection) connection.setUser(user);
    }

    @Override
    public void setPlayer(Object channel, Object player) {
        if (channel instanceof PacketEventsPlayerConnection connection) connection.setPacketEventsPlayer(player);
    }

    @Override
    public boolean isPlayerSet(Object channel) {
        return channel instanceof PacketEventsPlayerConnection connection && connection.getPacketEventsPlayer() != null;
    }

    @Override
    public boolean isProxy() {
        return false;
    }
}
