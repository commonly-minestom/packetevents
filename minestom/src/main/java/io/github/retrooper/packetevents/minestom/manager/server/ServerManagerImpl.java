package io.github.retrooper.packetevents.minestom.manager.server;

import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.mappings.GlobalRegistryHolder;
import net.minestom.server.MinecraftServer;

public final class ServerManagerImpl implements ServerManager {

    private final ServerVersion version = resolveVersion();

    @Override
    public ServerVersion getVersion() {
        return this.version;
    }

    @Override
    public Object getRegistryCacheKey(User user, ClientVersion version) {
        return GlobalRegistryHolder.getGlobalRegistryCacheKey(user, version);
    }

    private static ServerVersion resolveVersion() {
        ServerVersion[] versions = ServerVersion.values();
        for (int i = versions.length - 1; i >= 0; i--) {
            if (versions[i].getReleaseName().equals(MinecraftServer.VERSION_NAME)) {
                return versions[i];
            }
        }
        for (int i = versions.length - 1; i >= 0; i--) {
            if (versions[i].getProtocolVersion() == MinecraftServer.PROTOCOL_VERSION) {
                return versions[i];
            }
        }
        throw new IllegalStateException("Unsupported Minestom version " + MinecraftServer.VERSION_NAME
                + " (protocol " + MinecraftServer.PROTOCOL_VERSION + ")");
    }
}
