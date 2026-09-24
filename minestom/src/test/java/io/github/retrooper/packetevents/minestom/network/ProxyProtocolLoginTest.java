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

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.UserLoginEvent;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import io.github.retrooper.packetevents.minestom.factory.MinestomPacketEventsAPI;
import io.github.retrooper.packetevents.minestom.factory.MinestomPacketEventsBuilder;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.client.handshake.ClientHandshakePacket;
import net.minestom.server.network.packet.client.login.ClientLoginAcknowledgedPacket;
import net.minestom.server.network.packet.client.login.ClientLoginStartPacket;
import net.minestom.server.network.packet.server.login.LoginSuccessPacket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// offline mode behind a PROXY protocol load balancer, with compression turned off
class ProxyProtocolLoginTest {

    private static final InetSocketAddress CLIENT_ADDRESS = new InetSocketAddress("203.0.113.7", 51234);
    private static final BlockingQueue<UserLoginEvent> LOGINS = new LinkedBlockingQueue<>();

    private static MinestomPacketEventsAPI api;
    private static int port;

    @BeforeAll
    static void startServer() {
        // minestom reads its flags once, gradle runs every test class in its own jvm
        System.setProperty("minestom.proxy-protocol", "true");

        MinecraftServer minecraftServer = MinecraftServer.init();
        MinecraftServer.setCompressionThreshold(0);

        Instance instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> event.setSpawningInstance(instance));

        api = MinestomPacketEventsBuilder.buildNoCache(new PacketEventsSettings().checkForUpdates(false));
        PacketEvents.setAPI(api);

        api.load();
        api.getEventManager().registerListener(new PacketListenerAbstract() {
            @Override
            public void onUserLogin(UserLoginEvent event) {
                LOGINS.add(event);
            }
        });
        api.start(minecraftServer, "127.0.0.1", 0);

        port = MinecraftServer.getServer().getPort();
    }

    @AfterAll
    static void stopServer() {
        api.terminate();
        MinecraftServer.stopCleanly();
    }

    @Test
    @Timeout(60)
    void proxiedOfflineLoginWithoutCompression() throws Exception {
        try (TestClient client = TestClient.connect(port)) {
            String header = "PROXY TCP4 " + CLIENT_ADDRESS.getHostString() + " 127.0.0.1 " + CLIENT_ADDRESS.getPort() + " " + port + "\r\n";
            client.sendRaw(header.getBytes(StandardCharsets.US_ASCII));

            client.send(new ClientHandshakePacket(MinecraftServer.PROTOCOL_VERSION, "localhost", port, ClientHandshakePacket.Intent.LOGIN));
            client.send(new ClientLoginStartPacket("Proxied", UUID.randomUUID()));

            LoginSuccessPacket success = assertInstanceOf(LoginSuccessPacket.class, client.receive());
            assertEquals("Proxied", success.gameProfile().name());
            assertEquals(0, client.compressionThreshold());

            client.send(new ClientLoginAcknowledgedPacket());
            client.completeConfiguration();

            UserLoginEvent login = LOGINS.poll(20, TimeUnit.SECONDS);
            assertNotNull(login, "user login event");

            assertEquals(CLIENT_ADDRESS, login.getUser().getAddress());
        }
    }
}
