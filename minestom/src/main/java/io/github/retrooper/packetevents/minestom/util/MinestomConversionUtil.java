package io.github.retrooper.packetevents.minestom.util;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import io.github.retrooper.packetevents.minestom.netty.buffer.PacketBuffer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.NetworkBuffer;
import org.jetbrains.annotations.Nullable;

public final class MinestomConversionUtil {

    private static final GameMode[] GAME_MODES = GameMode.values();

    private MinestomConversionUtil() {
    }

    public static Location fromMinestomPos(Pos pos) {
        return new Location(pos.x(), pos.y(), pos.z(), pos.yaw(), pos.pitch());
    }

    public static Pos toMinestomPos(Location location) {
        return new Pos(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public static Vector3d fromMinestomPoint(Point point) {
        return new Vector3d(point.x(), point.y(), point.z());
    }

    public static Vec toMinestomVec(Vector3d vector) {
        return new Vec(vector.getX(), vector.getY(), vector.getZ());
    }

    public static com.github.retrooper.packetevents.protocol.player.GameMode fromMinestomGameMode(GameMode gameMode) {
        return com.github.retrooper.packetevents.protocol.player.GameMode.getById(gameMode.ordinal());
    }

    public static GameMode toMinestomGameMode(com.github.retrooper.packetevents.protocol.player.GameMode gameMode) {
        return GAME_MODES[gameMode.getId()];
    }

    public static WrappedBlockState fromMinestomBlock(Block block) {
        return WrappedBlockState.getByGlobalId(version(), block.stateId());
    }

    public static @Nullable Block toMinestomBlock(WrappedBlockState state) {
        return Block.fromStateId(state.getGlobalId());
    }

    public static com.github.retrooper.packetevents.protocol.entity.type.EntityType fromMinestomEntityType(EntityType type) {
        return EntityTypes.getById(version(), type.id());
    }

    public static @Nullable EntityType toMinestomEntityType(
            com.github.retrooper.packetevents.protocol.entity.type.EntityType type) {
        return EntityType.fromId(type.getId(version()));
    }

    public static @Nullable ItemType fromMinestomMaterial(Material material) {
        return ItemTypes.getById(version(), material.id());
    }

    public static @Nullable Material toMinestomMaterial(ItemType type) {
        return Material.fromId(type.getId(version()));
    }

    public static com.github.retrooper.packetevents.protocol.item.ItemStack fromMinestomItemStack(ItemStack itemStack) {
        NetworkBuffer buffer = NetworkBuffer.resizableBuffer(MinecraftServer.getRegistries());
        buffer.write(ItemStack.NETWORK_TYPE, itemStack);
        return PacketWrapper.createUniversalPacketWrapper(new PacketBuffer(buffer)).readItemStack();
    }

    public static ItemStack toMinestomItemStack(com.github.retrooper.packetevents.protocol.item.ItemStack itemStack) {
        NetworkBuffer buffer = NetworkBuffer.resizableBuffer(MinecraftServer.getRegistries());
        PacketWrapper.createUniversalPacketWrapper(new PacketBuffer(buffer)).writeItemStack(itemStack);
        return buffer.read(ItemStack.NETWORK_TYPE);
    }

    private static ClientVersion version() {
        return PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    }
}
