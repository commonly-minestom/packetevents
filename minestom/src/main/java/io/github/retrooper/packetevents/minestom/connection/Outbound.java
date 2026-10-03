package io.github.retrooper.packetevents.minestom.connection;

import io.github.retrooper.packetevents.minestom.netty.buffer.PacketBuffer;

public record Outbound(PacketBuffer buffer, boolean silent) {
}
