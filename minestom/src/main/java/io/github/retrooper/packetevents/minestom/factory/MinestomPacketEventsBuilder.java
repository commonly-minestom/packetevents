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

import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerProcess;
import org.jetbrains.annotations.Nullable;

/**
 * Builds packetevents for Minestom, the server then has to be started through the returned api:
 * <pre>{@code
 * MinecraftServer server = MinecraftServer.init();
 * MinestomPacketEventsAPI api = MinestomPacketEventsBuilder.build();
 * PacketEvents.setAPI(api);
 * api.load();
 * // register listeners
 * api.start(server, "0.0.0.0", 25565);
 * }</pre>
 */
public final class MinestomPacketEventsBuilder {

    private static @Nullable MinestomPacketEventsAPI instance;

    private MinestomPacketEventsBuilder() {
    }

    public static void clearBuildCache() {
        instance = null;
    }

    public static MinestomPacketEventsAPI build() {
        return build(new PacketEventsSettings());
    }

    public static MinestomPacketEventsAPI build(PacketEventsSettings settings) {
        if (instance == null) instance = buildNoCache(settings);

        return instance;
    }

    public static MinestomPacketEventsAPI buildNoCache() {
        return buildNoCache(new PacketEventsSettings());
    }

    public static MinestomPacketEventsAPI buildNoCache(PacketEventsSettings settings) {
        ServerProcess process = MinecraftServer.process();
        if (process == null) throw new IllegalStateException("MinecraftServer#init must be called before building packetevents");

        return new MinestomPacketEventsAPI(process, settings);
    }
}
