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
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.UserConnectEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.event.UserLoginEvent;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import com.github.retrooper.packetevents.wrapper.status.server.WrapperStatusServerResponse;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.retrooper.packetevents.minestom.factory.MinestomPacketEventsAPI;
import io.github.retrooper.packetevents.minestom.factory.MinestomPacketEventsBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.client.common.ClientPingRequestPacket;
import net.minestom.server.network.packet.client.common.ClientPluginMessagePacket;
import net.minestom.server.network.packet.client.configuration.ClientFinishConfigurationPacket;
import net.minestom.server.network.packet.client.configuration.ClientSelectKnownPacksPacket;
import net.minestom.server.network.packet.client.handshake.ClientHandshakePacket;
import net.minestom.server.network.packet.client.login.ClientEncryptionResponsePacket;
import net.minestom.server.network.packet.client.login.ClientLoginAcknowledgedPacket;
import net.minestom.server.network.packet.client.login.ClientLoginStartPacket;
import net.minestom.server.network.packet.client.status.StatusRequestPacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.common.PingResponsePacket;
import net.minestom.server.network.packet.server.common.PluginMessagePacket;
import net.minestom.server.network.packet.server.configuration.FinishConfigurationPacket;
import net.minestom.server.network.packet.server.configuration.SelectKnownPacksPacket;
import net.minestom.server.network.packet.server.configuration.UpdateEnabledFeaturesPacket;
import net.minestom.server.network.packet.server.login.EncryptionRequestPacket;
import net.minestom.server.network.packet.server.login.LoginSuccessPacket;
import net.minestom.server.network.packet.server.login.SetCompressionPacket;
import net.minestom.server.network.packet.server.play.JoinGamePacket;
import net.minestom.server.network.packet.server.play.SystemChatPacket;
import net.minestom.server.network.packet.server.status.ResponsePacket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

// boots a real online-mode minestom server through packetevents and drives it with a raw protocol client
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MinestomPacketEventsTest {

    private static final String USERNAME = "PacketEvents";
    private static final UUID PLAYER_UUID = UUID.fromString("7c6f3f44-8d4b-4c38-9d57-2b56d7cfae41");
    private static final int COMPRESSION_THRESHOLD = 64;
    private static final long TIMEOUT_SECONDS = 20;
    private static final Component MINESTOM_MESSAGE = Component.text("from minestom");
    private static final Component PACKETEVENTS_MESSAGE = Component.text("from packetevents", NamedTextColor.AQUA);
    private static final Component SILENT_MESSAGE = Component.text("silent");
    private static final Component REWRITTEN_MESSAGE = Component.text("rewritten", NamedTextColor.GOLD)
            .clickEvent(ClickEvent.runCommand("/packetevents"))
            .hoverEvent(HoverEvent.showText(Component.text("hover")));

    private final RecordingListener listener = new RecordingListener();
    private final BlockingQueue<String> pluginMessages = new LinkedBlockingQueue<>();

    private HttpServer sessionServer;
    private MinestomPacketEventsAPI api;
    private int port;

    @BeforeAll
    void startServer() throws IOException {
        sessionServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        sessionServer.createContext("/hasJoined", MinestomPacketEventsTest::answerSession);
        sessionServer.start();

        // minestom reads it once when its flags load, so before anything touches MinecraftServer
        System.setProperty("minestom.auth.url", "http://127.0.0.1:" + sessionServer.getAddress().getPort() + "/hasJoined");

        MinecraftServer minecraftServer = MinecraftServer.init(new Auth.Online());
        MinecraftServer.setCompressionThreshold(COMPRESSION_THRESHOLD);

        Instance instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        GlobalEventHandler events = MinecraftServer.getGlobalEventHandler();

        events.addListener(AsyncPlayerConfigurationEvent.class, event -> event.setSpawningInstance(instance));
        events.addListener(PlayerPluginMessageEvent.class, event -> pluginMessages.add(event.getIdentifier()));

        api = MinestomPacketEventsBuilder.buildNoCache(new PacketEventsSettings().checkForUpdates(false));
        PacketEvents.setAPI(api);

        api.load();
        api.getEventManager().registerListener(listener);
        api.start(minecraftServer, "127.0.0.1", 0);

        port = MinecraftServer.getServer().getPort();
    }

    @AfterAll
    void stopServer() {
        api.terminate();
        MinecraftServer.stopCleanly();
        sessionServer.stop(0);
    }

    @Test
    @Order(0)
    void minestomReportsTheRealAddress() {
        assertTrue(MinecraftServer.getServer().isOpen());
        assertEquals("127.0.0.1", MinecraftServer.getServer().getAddress());
        assertTrue(port > 0);
        assertEquals(0, MinecraftServer.getCompressionThreshold(), "minestom must frame packets uncompressed");
    }

    @Test
    @Order(1)
    @Timeout(60)
    void statusResponseIsRewritten() throws IOException {
        try (TestClient client = TestClient.connect(port)) {
            client.send(new ClientHandshakePacket(MinecraftServer.PROTOCOL_VERSION, "localhost", port, ClientHandshakePacket.Intent.STATUS));
            client.send(new StatusRequestPacket());

            ResponsePacket response = assertInstanceOf(ResponsePacket.class, client.receive());
            assertTrue(response.jsonResponse().contains("\"description\":\"packetevents\""), response.jsonResponse());

            client.send(new ClientPingRequestPacket(42));
            PingResponsePacket pong = assertInstanceOf(PingResponsePacket.class, client.receive());

            assertEquals(42, pong.number());
        }

        assertTrue(listener.received.contains(PacketType.Handshaking.Client.HANDSHAKE));
        assertTrue(listener.received.contains(PacketType.Status.Client.REQUEST));
        assertTrue(listener.sent.contains(PacketType.Status.Server.PONG));
    }

    @Test
    @Order(2)
    @Timeout(60)
    void onlineLoginReachesPlay() throws Exception {
        List<String> channels = new ArrayList<>();
        boolean featuresReceived = false;

        try (TestClient client = TestClient.connect(port)) {
            client.send(new ClientHandshakePacket(MinecraftServer.PROTOCOL_VERSION, "localhost", port, ClientHandshakePacket.Intent.LOGIN));
            client.send(new ClientLoginStartPacket(USERNAME, PLAYER_UUID));

            EncryptionRequestPacket request = assertInstanceOf(EncryptionRequestPacket.class, client.receive());
            SecretKey secret = new SecretKeySpec(randomBytes(16), "AES");
            PublicKey serverKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(request.publicKey()));

            client.send(new ClientEncryptionResponsePacket(rsa(serverKey, secret.getEncoded()), rsa(serverKey, request.verifyToken())));
            client.enableEncryption(secret);

            SetCompressionPacket compression = assertInstanceOf(SetCompressionPacket.class, client.receive());
            assertEquals(COMPRESSION_THRESHOLD, compression.threshold());

            LoginSuccessPacket success = assertInstanceOf(LoginSuccessPacket.class, client.receive());
            assertEquals(USERNAME, success.gameProfile().name());

            client.send(new ClientLoginAcknowledgedPacket());

            while (true) {
                ServerPacket packet = client.receive();

                if (packet instanceof PluginMessagePacket message) channels.add(message.channel());
                if (packet instanceof UpdateEnabledFeaturesPacket) featuresReceived = true;
                if (packet instanceof FinishConfigurationPacket) break;

                if (packet instanceof SelectKnownPacksPacket) {
                    client.send(new ClientPluginMessagePacket("test:cancelled", new byte[0]));
                    client.send(new ClientPluginMessagePacket("test:rewritten", new byte[0]));
                    client.send(new ClientSelectKnownPacksPacket(List.of(SelectKnownPacksPacket.MINECRAFT_CORE)));
                }
            }

            client.send(new ClientFinishConfigurationPacket());
            client.receiveUntil(JoinGamePacket.class);

            assertEquals(List.of("test:before", "minecraft:brand", "test:after"), channels);
            assertFalse(featuresReceived, "cancelled packets must not reach the client");
            assertEquals(COMPRESSION_THRESHOLD, client.compressionThreshold());

            UserLoginEvent login = listener.logins.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertNotNull(login, "user login event");

            User user = login.getUser();
            Player player = login.getPlayer();

            assertEquals(USERNAME, user.getProfile().getName());
            assertEquals(PLAYER_UUID, user.getUUID());
            assertEquals(ClientVersion.getById(MinecraftServer.PROTOCOL_VERSION), user.getClientVersion());
            assertSame(user, api.getPlayerManager().getUser(player));
            assertSame(player.getPlayerConnection(), user.getChannel());
            assertSame(user.getChannel(), api.getProtocolManager().getChannel(PLAYER_UUID));

            Set<String> received = new HashSet<>();

            while (received.size() < 2) {
                String identifier = pluginMessages.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                assertNotNull(identifier, "plugin messages seen by minestom: " + received);

                received.add(identifier);
            }

            assertEquals(Set.of("test:injected", "test:modified"), received);

            // components go through packetevents' own serializers on minestom's adventure version
            player.sendMessage(MINESTOM_MESSAGE);
            assertEquals(REWRITTEN_MESSAGE, client.receiveUntil(SystemChatPacket.class).message());

            api.getPlayerManager().sendPacket(player, new WrapperPlayServerSystemChatMessage(false, PACKETEVENTS_MESSAGE));
            assertEquals(PACKETEVENTS_MESSAGE, client.receiveUntil(SystemChatPacket.class).message());

            int sentBefore = listener.sent.size();
            api.getPlayerManager().sendPacketSilently(player, new WrapperPlayServerSystemChatMessage(false, SILENT_MESSAGE));

            assertEquals(SILENT_MESSAGE, client.receiveUntil(SystemChatPacket.class).message());
            assertFalse(listener.sent.subList(sentBefore, listener.sent.size()).contains(PacketType.Play.Server.SYSTEM_CHAT_MESSAGE));

            user.receivePacket(new WrapperPlayClientPluginMessage("test:play", new byte[0]));
            assertEquals("test:play", pluginMessages.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            CountDownLatch loopTask = new CountDownLatch(1);
            ChannelHelper.runInEventLoop(user.getChannel(), loopTask::countDown);
            assertTrue(loopTask.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "event loop task");

            assertConcurrentSendsKeepTheirOrder(user, client);
        }

        UserDisconnectEvent disconnect = listener.disconnects.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(disconnect, "user disconnect event");
        assertEquals(USERNAME, disconnect.getUser().getProfile().getName());

        assertTrue(listener.connects.size() >= 2);
        assertTrue(listener.received.contains(PacketType.Login.Client.ENCRYPTION_RESPONSE));
        assertTrue(listener.received.contains(PacketType.Configuration.Client.CONFIGURATION_END_ACK));
        assertTrue(listener.sent.contains(PacketType.Login.Server.SET_COMPRESSION));
        assertTrue(listener.sent.contains(PacketType.Play.Server.JOIN_GAME));
    }

    // packets sent from several threads at once must all arrive, each thread's packets in order
    private static void assertConcurrentSendsKeepTheirOrder(User user, TestClient client) throws Exception {
        int threads = 4;
        int packetsPerThread = 250;
        CountDownLatch sent = new CountDownLatch(threads);

        for (int thread = 0; thread < threads; thread++) {
            int id = thread;

            Thread.ofVirtual().start(() -> {
                for (int i = 0; i < packetsPerThread; i++) user.sendPacket(new WrapperPlayServerSystemChatMessage(false, Component.text(id + ":" + i)));

                sent.countDown();
            });
        }

        assertTrue(sent.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "concurrent senders");

        int[] next = new int[threads];

        for (int received = 0; received < threads * packetsPerThread; received++) {
            Component message = client.receiveUntil(SystemChatPacket.class).message();
            String[] parts = ((TextComponent) message).content().split(":");
            int thread = Integer.parseInt(parts[0]);

            assertEquals(next[thread], Integer.parseInt(parts[1]), "order of thread " + thread);

            next[thread]++;
        }
    }

    private static void answerSession(HttpExchange exchange) throws IOException {
        String body = "{\"id\":\"" + PLAYER_UUID.toString().replace("-", "") + "\",\"name\":\"" + USERNAME + "\",\"properties\":[]}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static byte[] rsa(PublicKey key, byte[] data) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);

        return cipher.doFinal(data);
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);

        return bytes;
    }

    private static final class RecordingListener extends PacketListenerAbstract {

        private final List<PacketTypeCommon> received = new CopyOnWriteArrayList<>();
        private final List<PacketTypeCommon> sent = new CopyOnWriteArrayList<>();
        private final List<User> connects = new CopyOnWriteArrayList<>();
        private final BlockingQueue<UserLoginEvent> logins = new LinkedBlockingQueue<>();
        private final BlockingQueue<UserDisconnectEvent> disconnects = new LinkedBlockingQueue<>();

        @Override
        public void onUserConnect(UserConnectEvent event) {
            connects.add(event.getUser());
        }

        @Override
        public void onUserLogin(UserLoginEvent event) {
            logins.add(event);
        }

        @Override
        public void onUserDisconnect(UserDisconnectEvent event) {
            if (event.getUser().getProfile().getName() != null) disconnects.add(event);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            received.add(event.getPacketType());

            boolean knownPacks = event.getPacketType() == PacketType.Configuration.Client.SELECT_KNOWN_PACKS;

            if (knownPacks) event.getUser().receivePacket(new WrapperConfigClientPluginMessage("test:injected", new byte[0]));

            if (event.getPacketType() != PacketType.Configuration.Client.PLUGIN_MESSAGE) return;

            WrapperConfigClientPluginMessage message = new WrapperConfigClientPluginMessage(event);

            if (message.getChannelName().equals("test:cancelled")) event.setCancelled(true);
            if (message.getChannelName().equals("test:rewritten")) message.setChannelName("test:modified");
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
            sent.add(event.getPacketType());

            if (event.getPacketType() == PacketType.Configuration.Server.UPDATE_ENABLED_FEATURES) event.setCancelled(true);

            if (event.getPacketType() == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
                WrapperPlayServerSystemChatMessage chat = new WrapperPlayServerSystemChatMessage(event);

                if (MINESTOM_MESSAGE.equals(chat.getMessage())) chat.setMessage(REWRITTEN_MESSAGE);
            }

            if (event.getPacketType() == PacketType.Status.Server.RESPONSE) {
                WrapperStatusServerResponse response = new WrapperStatusServerResponse(event);
                JsonObject component = response.getComponent();

                component.addProperty("description", "packetevents");
                response.setComponent(component);
            }

            if (event.getPacketType() != PacketType.Configuration.Server.PLUGIN_MESSAGE) return;

            WrapperConfigServerPluginMessage message = new WrapperConfigServerPluginMessage(event);
            if (!message.getChannelName().equals("minecraft:brand")) return;

            User user = event.getUser();

            user.sendPacket(new WrapperConfigServerPluginMessage("test:before", new byte[0]));
            event.getTasksAfterSend().add(() -> user.sendPacket(new WrapperConfigServerPluginMessage("test:after", new byte[0])));
        }
    }
}
