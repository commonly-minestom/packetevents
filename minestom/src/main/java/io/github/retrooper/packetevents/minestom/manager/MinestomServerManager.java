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

import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.mappings.GlobalRegistryHolder;
import io.github.retrooper.packetevents.impl.netty.manager.server.ServerManagerAbstract;
import net.minestom.server.ping.Status;

public final class MinestomServerManager extends ServerManagerAbstract {

    // read at runtime, MinecraftServer's version constants would be inlined at compile time
    private final ServerVersion version = resolveVersion(Status.VersionInfo.DEFAULT);

    @Override
    public ServerVersion getVersion() {
        return version;
    }

    @Override
    public Object getRegistryCacheKey(User user, ClientVersion version) {
        return GlobalRegistryHolder.getGlobalRegistryCacheKey(user, version);
    }

    private static ServerVersion resolveVersion(Status.VersionInfo info) {
        ServerVersion sameProtocol = null;

        for (ServerVersion version : ServerVersion.reversedValues()) {
            if (version.getReleaseName().equals(info.name())) return version;

            if (sameProtocol == null && version.getProtocolVersion() == info.protocolVersion()) sameProtocol = version;
        }

        if (sameProtocol != null) return sameProtocol;

        throw new IllegalStateException("PacketEvents doesn't support Minecraft version " + info.name()
                + " (protocol " + info.protocolVersion() + "); if you believe this is an error, please report it to PacketEvents");
    }
}
