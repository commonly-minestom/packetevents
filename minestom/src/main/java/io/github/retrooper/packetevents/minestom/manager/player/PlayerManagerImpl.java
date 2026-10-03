package io.github.retrooper.packetevents.minestom.manager.player;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlayerManagerImpl implements PlayerManager {

    @Override
    public int getPing(@NotNull Object player) {
        return ((Player) player).getLatency();
    }

    @Override
    public @NotNull ClientVersion getClientVersion(@NotNull Object player) {
        return PacketEvents.getAPI().getProtocolManager().getClientVersion(getChannel(player));
    }

    @Override
    public @Nullable Object getChannel(@NotNull Object player) {
        return PacketEvents.getAPI().getProtocolManager().getChannel(((Player) player).getUuid());
    }

    @Override
    public @Nullable User getUser(@NotNull Object player) {
        Object channel = getChannel(player);
        return channel == null ? null : PacketEvents.getAPI().getProtocolManager().getUser(channel);
    }
}
