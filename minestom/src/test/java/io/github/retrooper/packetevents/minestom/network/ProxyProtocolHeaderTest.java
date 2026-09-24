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

package io.github.retrooper.packetevents.minestom.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProxyProtocolHeaderTest {

    private static final byte[] V2_SIGNATURE = {0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A};

    @Test
    void findsV1HeaderEnd() throws IOException {
        String header = "PROXY TCP4 192.0.2.1 198.51.100.1 56324 25565\r\n";
        ByteBuf buffer = ascii(header + "minecraft");

        assertEquals(header.length(), ProxyProtocolHeader.length(buffer));
    }

    @Test
    void waitsForSplitHeaders() throws IOException {
        assertEquals(ProxyProtocolHeader.NEED_MORE, ProxyProtocolHeader.length(ascii("PROXY TCP4 192.0")));
        assertEquals(ProxyProtocolHeader.NEED_MORE, ProxyProtocolHeader.length(Unpooled.wrappedBuffer(V2_SIGNATURE, 0, 7)));
        assertEquals(ProxyProtocolHeader.NEED_MORE, ProxyProtocolHeader.length(Unpooled.EMPTY_BUFFER));
    }

    @Test
    void findsV2HeaderEnd() throws IOException {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeBytes(V2_SIGNATURE);
        buffer.writeByte(0x21);
        buffer.writeByte(0x11);
        buffer.writeShort(12);
        buffer.writeZero(12 + 5);

        assertEquals(28, ProxyProtocolHeader.length(buffer));
    }

    @Test
    void ignoresMinecraftFrames() throws IOException {
        ByteBuf handshake = Unpooled.wrappedBuffer(new byte[]{0x10, 0x00, (byte) 0x88, 0x06});
        ByteBuf longHandshake = Unpooled.wrappedBuffer(new byte[]{'P', 0x00, (byte) 0x88, 0x06});

        assertEquals(ProxyProtocolHeader.ABSENT, ProxyProtocolHeader.length(handshake));
        assertEquals(ProxyProtocolHeader.ABSENT, ProxyProtocolHeader.length(longHandshake));
    }

    @Test
    void rejectsUnterminatedV1Header() {
        ByteBuf buffer = ascii("PROXY " + "1".repeat(120));

        assertThrows(IOException.class, () -> ProxyProtocolHeader.length(buffer));
    }

    private static ByteBuf ascii(String text) {
        return Unpooled.copiedBuffer(text, StandardCharsets.US_ASCII);
    }
}
