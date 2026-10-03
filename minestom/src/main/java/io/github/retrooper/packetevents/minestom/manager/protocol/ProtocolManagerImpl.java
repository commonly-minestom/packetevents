package io.github.retrooper.packetevents.minestom.manager.protocol;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.protocol.ProtocolVersion;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import io.github.retrooper.packetevents.minestom.connection.PacketConnection;
import io.github.retrooper.packetevents.minestom.netty.buffer.PacketBuffer;

public final class ProtocolManagerImpl implements ProtocolManager {

    @Override
    public ProtocolVersion getPlatformVersion() {
        return ProtocolVersion.UNKNOWN;
    }

    @Override
    public void sendPacket(Object channel, Object byteBuf) {
        ((PacketConnection) channel).send((PacketBuffer) byteBuf, false);
    }

    @Override
    public void sendPacketSilently(Object channel, Object byteBuf) {
        ((PacketConnection) channel).send((PacketBuffer) byteBuf, true);
    }

    @Override
    public void writePacket(Object channel, Object byteBuf) {
        ((PacketConnection) channel).send((PacketBuffer) byteBuf, false);
    }

    @Override
    public void writePacketSilently(Object channel, Object byteBuf) {
        ((PacketConnection) channel).send((PacketBuffer) byteBuf, true);
    }

    @Override
    public void receivePacket(Object channel, Object byteBuf) {
        ((PacketConnection) channel).receive((PacketBuffer) byteBuf, false);
    }

    @Override
    public void receivePacketSilently(Object channel, Object byteBuf) {
        ((PacketConnection) channel).receive((PacketBuffer) byteBuf, true);
    }

    @Override
    public ClientVersion getClientVersion(Object channel) {
        User user = getUser(channel);
        ClientVersion version = user == null ? null : user.getClientVersion();
        if (version != null) {
            return version;
        }
        return PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    }
}
