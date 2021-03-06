package net.iceyleagons.slimelink.server;

import lombok.SneakyThrows;
import net.iceyleagons.slimelink.VoicePacket;
import net.iceyleagons.slimelink.VoicePacket.PacketType;
import net.iceyleagons.slimelink.server.ServerUtils.RemoteClient;
import net.iceyleagons.slimelink.utils.GZIPUtils;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class WSServer extends WebSocketServer {
    private String settings;

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

    private void handlePacket(WebSocket conn, VoicePacket packet) {
        if (!ServerUtils.clientMap.containsKey(packet.playerName)) {
            if (packet.packetType.equals(PacketType.SYNC)) {
                ServerUtils.clientMap.put(packet.playerName, new RemoteClient<>(conn, null, this::broadcast, (remoteClient, voicePacket) -> broadcast(remoteClient, PacketType.VOICE, voicePacket),
                        this::broadcastState, WebSocket::close));
                createPacket(PacketType.SYNC, settings.replace("usER-id_H3r3", ServerUtils.clientMap.get(packet.playerName).userId)).send(conn);
            }
            return;
        }

        ServerUtils.receivedPacket(packet.playerName);

        switch (packet.packetType) {
            default:
            case VOICE:
                ServerUtils.clientMap.get(packet.playerName).handleVoice(packet);
                break;
            case STATE:
                ServerUtils.clientMap.get(packet.playerName).handleState(packet);
                break;
            case READY:
                // Player is ready to receive voice!
                break;
            case SYNC:
                createPacket(PacketType.SYNC, settings).send(conn);
                break;
            case CLIENT_LEAVE:
                ServerUtils.clientMap.remove(packet.playerName);
                conn.close();
                break;
        }
    }

    @Override
    public void onClosing(WebSocket conn, int code, String reason, boolean remote) {
        ServerUtils.clientMap.entrySet().stream()
                .filter(entry -> entry.getValue().getSocket().equals(conn))
                .findAny()
                .ifPresent(stringRemoteClientEntry -> ServerUtils.clientMap.remove(stringRemoteClientEntry.getKey()));
        super.onClosing(conn, code, reason, remote);
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

    public void broadcast(RemoteClient sender, PacketType packetType, VoicePacket originalPacket) {
        VoicePacket packet = clonePacket(packetType, originalPacket, false);

        if (!ServerUtils.debug) {
            Arrays.stream(sender.players)
                    .map(s -> s.strip().replace(" ", ""))
                    .filter(name -> !name.equals(originalPacket.playerName))
                    .filter(ServerUtils.clientMap::containsKey)
                    .map(ServerUtils.clientMap::get)
                    .filter(client -> packet.dead == client.dead || !packet.dead || ServerUtils.impostorChat && packet.impostor && client.isImpostor())
                    .filter(client -> ServerUtils.serverSettings.hearingRange > packet.position.distanceTo(client.lastKnownPosition))
                    .forEach(client -> {
                        if (client.isImpostor() && packet.impostor)
                            client.sendPacket(clonePacket(packetType, originalPacket, true));
                        else client.sendPacket(packet);
                    });
        } else ServerUtils.clientMap.get(packet.playerName).sendPacket(packet);
    }

    public void broadcastState(RemoteClient sender, VoicePacket originalPacket) {
        VoicePacket packet = cloneStatePacket(originalPacket, ServerUtils.gson.fromJson(originalPacket.data, VoicePacket.StateData.class));

        if (!ServerUtils.debug) {
            Arrays.stream(sender.players)
                    .map(s -> s.strip().replace(" ", ""))
                    .filter(name -> !name.equals(originalPacket.playerName))
                    .filter(ServerUtils.clientMap::containsKey)
                    .map(ServerUtils.clientMap::get)
                    .forEach(client -> client.sendPacket(packet));
        } else ServerUtils.clientMap.get(packet.playerName).sendPacket(packet);
    }

    public VoicePacket createPacket(PacketType packetType, String data) {
        VoicePacket packet;
        if (data != null)
            packet = new VoicePacket("-1", packetType, "SERVER", false, false, data, new VoicePacket.Position(0, 0, 0));
        else packet = new VoicePacket("-1", packetType, "SERVER", false, false);

        return packet;
    }

    public VoicePacket clonePacket(PacketType packetType, VoicePacket packet, boolean copyImpostor) {
        return new VoicePacket("0", packetType, packet.playerName, packet.dead, copyImpostor && packet.impostor, packet.data, packet.position);
    }

    public VoicePacket cloneStatePacket(VoicePacket packet, VoicePacket.StateData stateData) {
        return new VoicePacket("0", PacketType.STATE, packet.playerName, false, false, ServerUtils.gson.toJson(new VoicePacket.StateData(stateData.muted, stateData.deafened, new String[0])), packet.position);
    }
}
