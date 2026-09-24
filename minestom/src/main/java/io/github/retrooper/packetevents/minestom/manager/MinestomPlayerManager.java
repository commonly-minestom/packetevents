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

package io.github.retrooper.packetevents.minestom.manager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import io.github.retrooper.packetevents.impl.netty.manager.player.PlayerManagerAbstract;
import io.github.retrooper.packetevents.minestom.network.PacketEventsPlayerConnection;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MinestomPlayerManager extends PlayerManagerAbstract {

    @Override
    public int getPing(@NotNull Object player) {
        return minestomPlayer(player).getLatency();
    }

    // null for players with custom connections, e.g. fake players
    @Override
    public @Nullable Object getChannel(@NotNull Object player) {
        return minestomPlayer(player).getPlayerConnection() instanceof PacketEventsPlayerConnection connection ? connection : null;
    }

    @Override
    public @Nullable User getUser(@NotNull Object player) {
        Object channel = getChannel(player);

        return channel != null ? PacketEvents.getAPI().getProtocolManager().getUser(channel) : null;
    }

    @Override
    public @NotNull ClientVersion getClientVersion(@NotNull Object player) {
        User user = getUser(player);
        if (user != null && user.getClientVersion() != null) return user.getClientVersion();

        return PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    }

    private static Player minestomPlayer(Object player) {
        if (player instanceof Player minestomPlayer) return minestomPlayer;

        throw new UnsupportedOperationException("Unsupported player implementation: " + player);
    }
}
