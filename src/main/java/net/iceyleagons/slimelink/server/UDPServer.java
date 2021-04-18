package net.iceyleagons.slimelink.server;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.iceyleagons.slimelink.VoicePacket;
import net.iceyleagons.slimelink.server.ServerUtils.RemoteClient;
import net.iceyleagons.slimelink.utils.GZIPUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;

public class UDPServer {

    private String settings;

    public void recomputeSettings() {
        settings = ServerUtils.gson.toJson(ServerUtils.serverSettings);
    }

    @AllArgsConstructor
    @Getter
    public static class SocketData {
        private final InetAddress address;
        private final int port;
    }

    DatagramSocket socket;

    public UDPServer(int port) {
        recomputeSettings();

        new Thread(() -> {
            try {
                socket = new DatagramSocket(port);
                while (true) {
                    DatagramPacket packet = new DatagramPacket(new byte[4096], 4096);
                    try {
                        socket.receive(packet);
                        handlePacket(packet.getAddress(), packet.getPort(), ServerUtils.gson.fromJson(new String(GZIPUtils.gzipUncompress(packet.getData())), VoicePacket.class));
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handlePacket(InetAddress conn, int port, VoicePacket packet) {
        if (!ServerUtils.clientMap.containsKey(packet.playerName)) {
            if (packet.packetType.equals(VoicePacket.PacketType.SYNC)) {
                ServerUtils.clientMap.put(packet.playerName, new RemoteClient<>(new SocketData(conn, port), null, (vp, conns) -> {
                    for (SocketData s : conns)
                        try {
                            socket.send(new DatagramPacket(vp, vp.length, s.getAddress(), s.getPort()));
                        } catch (IOException ioException) {
                            ioException.printStackTrace();
                        }
                }, (remoteClient, voicePacket) -> broadcast(remoteClient, VoicePacket.PacketType.VOICE, voicePacket),
                        this::broadcastState, ignored -> {
                }));

                createPacket(VoicePacket.PacketType.SYNC, settings.replace("usER-id_H3r3", ServerUtils.clientMap.get(packet.playerName).userId)).send(socket, conn, port);
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
                createPacket(VoicePacket.PacketType.SYNC, settings);
                break;
            case CLIENT_LEAVE:
                ServerUtils.clientMap.remove(packet.playerName);
                break;
        }
    }

    public void broadcast(RemoteClient sender, VoicePacket.PacketType packetType, VoicePacket originalPacket) {
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
        } else ServerUtils.clientMap.get(originalPacket.playerName).sendPacket(packet);
    }

    public VoicePacket createPacket(VoicePacket.PacketType packetType, String data) {
        VoicePacket packet;
        if (data != null)
            packet = new VoicePacket("-1", packetType, "SERVER", false, false, data, new VoicePacket.Position(0, 0, 0));
        else packet = new VoicePacket("-1", packetType, "SERVER", false, false);

        return packet;
    }

    public VoicePacket clonePacket(VoicePacket.PacketType packetType, VoicePacket packet, boolean copyImpostor) {
        return new VoicePacket("0", packetType, packet.playerName, packet.dead, copyImpostor && packet.impostor, packet.data, packet.position);
    }

    public VoicePacket cloneStatePacket(VoicePacket packet, VoicePacket.StateData stateData) {
        return new VoicePacket("0", VoicePacket.PacketType.STATE, packet.playerName, false, false, ServerUtils.gson.toJson(new VoicePacket.StateData(stateData.muted, stateData.deafened, new String[0])), packet.position);
    }

}
