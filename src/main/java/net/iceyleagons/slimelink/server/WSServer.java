package net.iceyleagons.slimelink.server;

import lombok.Getter;
import lombok.SneakyThrows;
import net.iceyleagons.slimelink.utils.Caesar;
import net.iceyleagons.slimelink.utils.GZIPUtils;
import net.iceyleagons.slimelink.VoicePacket;
import net.iceyleagons.slimelink.VoicePacket.PacketType;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WSServer extends WebSocketServer {
    private String settings;

    public static Map<String, RemoteClient> clientMap = new ConcurrentHashMap<>();

    public void recomputeSettings() {
        settings = ServerUtils.gson.toJson(ServerUtils.serverSettings);
    }

    public WSServer(InetSocketAddress address) {
        super(address);
        recomputeSettings();
    }

    @SneakyThrows
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("Connection from " +  conn.getRemoteSocketAddress().getAddress().getHostAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {

    }

    @SneakyThrows
    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        VoicePacket packet = ServerUtils.gson.fromJson(new String(GZIPUtils.gzipUncompress(message.array())), VoicePacket.class);

        handlePacket(conn, packet);
    }

    private void handlePacket(WebSocket conn, VoicePacket packet) throws IOException {
        if (!clientMap.containsKey(packet.playerName)) {
            if (packet.packetType.equals(PacketType.SYNC)) {
                clientMap.put(packet.playerName, new RemoteClient(conn, null));
                createPacket(PacketType.SYNC, settings).send(conn);
            }
            return;
        }

        switch (packet.packetType) {
            default:
            case VOICE:
                broadcast(PacketType.VOICE, packet);
                break;
            case READY:
                // Player is ready to receive voice!
                break;
            case SYNC:
                createPacket(PacketType.SYNC, settings).send(conn);
                break;
            case CLIENT_LEAVE:
                clientMap.remove(packet.playerName);
                break;
        }
    }

    @SneakyThrows
    @Override
    public void onMessage(WebSocket conn, String message) {
        VoicePacket packet = ServerUtils.gson.fromJson(message, VoicePacket.class);

        handlePacket(conn, packet);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {

    }

    @Override
    public void onStart() {

    }

    public void broadcast(byte[] message, RemoteClient... clients) {
        if (clients != null)
            if (clients.length != 0)
                broadcast(message, Arrays.stream(clients).map(RemoteClient::getSocket).collect(Collectors.toSet()));
    }

    public void broadcast(PacketType packetType, VoicePacket originalPacket) {
        VoicePacket packet = clonePacket(packetType, originalPacket, false);

        if (!ServerUtils.debug)
            Arrays.stream(packet.playerNames)
                    .filter(name -> !name.equals(originalPacket.playerName))
                    .filter(clientMap::containsKey)
                    .map(clientMap::get)
                    .filter(client -> packet.dead == client.dead || !packet.dead || ServerUtils.impostorChat && packet.impostor && client.isImpostor())
                    .filter(client -> ServerUtils.serverSettings.hearingRange > packet.position.distanceTo(client.lastKnownPosition))
                    .forEach(client -> {
                        if (client.isImpostor() && packet.impostor)
                            client.sendPacket(clonePacket(packetType, originalPacket, true));
                        else client.sendPacket(packet);
                    });
        else clientMap.get(packet.playerName).sendPacket(packet);
    }

    public VoicePacket createPacket(PacketType packetType, String data) {
        VoicePacket packet;
        if (data != null)
            packet = new VoicePacket(packetType, "SERVER", new String[0], false, false, data, new VoicePacket.Position(0, 0, 0));
        else packet = new VoicePacket(packetType, "SERVER", new String[0], false, false);

        return packet;
    }

    public VoicePacket clonePacket(PacketType packetType, VoicePacket packet, boolean copyImpostor) {
        return new VoicePacket(packetType, packet.playerName, packet.playerNames, packet.dead, copyImpostor && packet.impostor, packet.data, packet.position);
    }

    @Getter
    public class RemoteClient {
        WebSocket socket;

        VoicePacket.Position lastKnownPosition = new VoicePacket.Position(0, 0, 0);
        boolean dead = false;
        boolean impostor = false;

        int caesar = 0;

        @SneakyThrows
        public RemoteClient(WebSocket socket, String caesar) {
            this.socket = socket;

            try {
                if (caesar != null)
                    this.caesar = Integer.parseInt(caesar);
            } catch (NumberFormatException ignored) {
                socket.close();
            }
        }

        @SneakyThrows
        public void disconnect() {
            socket.close();
            socket = null;
        }

        @SneakyThrows
        public void sendPacket(VoicePacket packet) {
            System.out.println("Sending packet{TYPE=" + packet.packetType.name().toUpperCase() + "}!");
            if (caesar != 0)
                broadcast(GZIPUtils.gzipCompress(Caesar.encrypt(packet.toString(), caesar).getBytes()), Collections.singleton(socket));
            else broadcast(packet.compress(), Collections.singleton(socket));
            //outputStream.writeUTF(VoiceServer.gson.toJson(packet));
        }

        public void handleVoice(VoicePacket packet) {
            this.lastKnownPosition = packet.position;
            this.dead = packet.dead;
            this.impostor = packet.impostor;

            broadcast(PacketType.VOICE, packet);
        }
    }
}
