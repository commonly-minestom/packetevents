package io.github.retrooper.packetevents.minestom.netty;

import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.netty.buffer.ByteBufAllocationOperator;
import com.github.retrooper.packetevents.netty.buffer.ByteBufOperator;
import com.github.retrooper.packetevents.netty.channel.ChannelOperator;
import io.github.retrooper.packetevents.minestom.netty.buffer.ByteBufAllocationOperatorImpl;
import io.github.retrooper.packetevents.minestom.netty.buffer.ByteBufOperatorImpl;
import io.github.retrooper.packetevents.minestom.netty.channel.ChannelOperatorImpl;

public final class NettyManagerImpl implements NettyManager {

    private final ChannelOperator channelOperator = new ChannelOperatorImpl();
    private final ByteBufOperator byteBufOperator = new ByteBufOperatorImpl();
    private final ByteBufAllocationOperator byteBufAllocationOperator = new ByteBufAllocationOperatorImpl();

    @Override
    public ChannelOperator getChannelOperator() {
        return this.channelOperator;
    }

    @Override
    public ByteBufOperator getByteBufOperator() {
        return this.byteBufOperator;
    }

    @Override
    public ByteBufAllocationOperator getByteBufAllocationOperator() {
        return this.byteBufAllocationOperator;
    }
}
