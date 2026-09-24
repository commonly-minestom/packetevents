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

package io.github.retrooper.packetevents.minestom;

import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.UserLoginEvent;
import io.github.retrooper.packetevents.minestom.network.PacketEventsPlayerConnection;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.network.player.PlayerSocketConnection;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class InternalMinestomListener {

    private final PacketEventsAPI<?> api;
    private final EventNode<Event> node = EventNode.all("packetevents");

    public InternalMinestomListener(PacketEventsAPI<?> api) {
        this.api = api;
        node.addListener(PlayerSpawnEvent.class, this::onSpawn);
    }

    public EventNode<Event> node() {
        return node;
    }

    // the first spawn is minestom's equivalent of a join
    private void onSpawn(PlayerSpawnEvent event) {
        if (!event.isFirstSpawn()) return;

        Player player = event.getPlayer();
        PlayerConnection connection = player.getPlayerConnection();

        if (connection instanceof PacketEventsPlayerConnection packetEventsConnection) {
            api.getEventManager().callEvent(new UserLoginEvent(packetEventsConnection.getUser(), player));

            return;
        }

        // fake players come with their own connection implementation
        if (!(connection instanceof PlayerSocketConnection)) return;

        boolean kick = !api.isTerminated() || api.getSettings().isKickIfTerminated();

        if (kick) player.kick(Component.text("PacketEvents failed to inject into a channel."));
    }
}
