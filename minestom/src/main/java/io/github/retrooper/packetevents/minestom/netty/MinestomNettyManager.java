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

package io.github.retrooper.packetevents.minestom.netty;

import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.netty.buffer.ByteBufAllocationOperator;
import com.github.retrooper.packetevents.netty.buffer.ByteBufOperator;
import com.github.retrooper.packetevents.netty.channel.ChannelOperator;
import io.github.retrooper.packetevents.impl.netty.buffer.ByteBufAllocationOperatorImpl;
import io.github.retrooper.packetevents.impl.netty.buffer.ByteBufOperatorImpl;

public final class MinestomNettyManager implements NettyManager {

    private final ChannelOperator channelOperator = new MinestomChannelOperator();
    private final ByteBufOperator byteBufOperator = new ByteBufOperatorImpl();
    private final ByteBufAllocationOperator byteBufAllocationOperator = new ByteBufAllocationOperatorImpl();

    @Override
    public ChannelOperator getChannelOperator() {
        return channelOperator;
    }

    @Override
    public ByteBufOperator getByteBufOperator() {
        return byteBufOperator;
    }

    @Override
    public ByteBufAllocationOperator getByteBufAllocationOperator() {
        return byteBufAllocationOperator;
    }
}
