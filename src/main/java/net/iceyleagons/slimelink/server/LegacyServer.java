package net.iceyleagons.slimelink.server;

import lombok.Getter;
import lombok.SneakyThrows;
import net.iceyleagons.slimelink.VoicePacket;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class LegacyServer {
    private ServerSocket serverSocket;

    private String settings;

    private final int port;

    public LegacyServer(int port) {
        recomputeSettings();
        this.port = port;
    }

    public Map<String, RemoteClient> playerMap = new HashMap<>();

    public void start() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);

                while (true) {
                    Socket socket = serverSocket.accept();

                    VoicePacket packet = ServerUtils.gson.fromJson(new DataInputStream(socket.getInputStream()).readUTF(), VoicePacket.class);
                    if (packet.packetType != VoicePacket.PacketType.SYNC) {
                        // Something really bad happened. Disconnect the client.
                        socket.close();
                    } else ServerUtils.executorService.execute(() -> onClientSync(socket, packet));
                }
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }).start();
    }

    public void onClientDisconnect(VoicePacket packet) {
        playerMap.remove(packet.playerName);
    }

    @SneakyThrows
    public void onClientSync(Socket socket, VoicePacket packet) {
        if (!playerMap.containsKey(packet.playerName)) {
            RemoteClient client = new RemoteClient(socket);
            client.sendPacket(createPacket(VoicePacket.PacketType.SYNC, settings));
            playerMap.put(packet.playerName, client);
        } else socket.close();
    }

    @Getter
    public class RemoteClient {
        Socket socket;
        DataInputStream inputStream;
        DataOutputStream outputStream;
        Thread receiveThread;
        boolean connected;

        VoicePacket.Position lastKnownPosition = new VoicePacket.Position(0, 0, 0);
        boolean dead = false;
        boolean impostor = false;

        @SneakyThrows
        public RemoteClient(Socket socket) {
            this.socket = socket;
            this.inputStream = new DataInputStream(socket.getInputStream());
            this.outputStream = new DataOutputStream(socket.getOutputStream());

            this.connected = true;

            this.receiveThread = new Thread(() -> {
                try {
                    while (connected) {
                        String packetRaw = inputStream.readUTF();
                        VoicePacket packet = ServerUtils.gson.fromJson(packetRaw, VoicePacket.class);

                        switch (packet.packetType) {
                            default:
                            case VOICE:
                                handleVoice(packet);
                                break;
                            case STATE:
                                broadcast(VoicePacket.PacketType.STATE, packet);
                                break;
                            case READY:
                                // Ignore.
                                break;
                            case CLIENT_LEAVE:
                                onClientDisconnect(packet);
                                disconnect();
                                break;
                            case SYNC:
                                // Something happened that caused the client to try to sync twice.
                                break;
                        }
                    }
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            });

            receiveThread.start();
        }

        @SneakyThrows
        public void disconnect() {
            connected = false;

            receiveThread.interrupt();
            receiveThread = null;

            socket.close();
            socket = null;

            inputStream.close();
            inputStream = null;

            outputStream.close();
            outputStream = null;
        }

        @SneakyThrows
        public void sendPacket(VoicePacket packet) {
            outputStream.writeUTF(ServerUtils.gson.toJson(packet));
        }

        public void handleVoice(VoicePacket packet) {
            this.lastKnownPosition = packet.position;
            this.dead = packet.dead;
            this.impostor = packet.impostor;

            broadcast(VoicePacket.PacketType.VOICE, packet);
        }
    }

    public void broadcast(VoicePacket.PacketType packetType, VoicePacket originalPacket) {
        ServerUtils.executorService.execute(() -> {
            VoicePacket packet = clonePacket(packetType, originalPacket, false);

            if (!ServerUtils.debug) {
                if (!packetType.equals(VoicePacket.PacketType.STATE))
                    Arrays.stream(packet.playerNames)
                            .filter(name -> !name.equals(originalPacket.playerName))
                            .filter(playerMap::containsKey)
                            .map(playerMap::get)
                            .filter(client -> packet.dead == client.dead || !packet.dead || ServerUtils.impostorChat && packet.impostor && client.isImpostor())
                            .filter(client -> ServerUtils.serverSettings.hearingRange > packet.position.distanceTo(client.lastKnownPosition))
                            .forEach(client -> {
                                if (client.isImpostor() && packet.impostor)
                                    client.sendPacket(clonePacket(packetType, originalPacket, true));
                                else client.sendPacket(packet);
                            });
                else Arrays.stream(packet.playerNames)
                        .filter(name -> !name.equals(originalPacket.playerName))
                        .filter(playerMap::containsKey)
                        .map(playerMap::get)
                        .forEach(client -> client.sendPacket(packet));
            } else playerMap.get(packet.playerName).sendPacket(packet);
        });
    }

    public void recomputeSettings() {
        settings = ServerUtils.gson.toJson(ServerUtils.serverSettings);
    }

    public VoicePacket createPacket(VoicePacket.PacketType packetType, String data) {
        VoicePacket packet;
        if (data != null)
            packet = new VoicePacket(packetType, "SERVER", new String[0], false, false, data, new VoicePacket.Position(0, 0, 0));
        else packet = new VoicePacket(packetType, "SERVER", new String[0], false, false);

        return packet;
    }

    public VoicePacket clonePacket(VoicePacket.PacketType packetType, VoicePacket packet, boolean copyImpostor) {
        return new VoicePacket(packetType, packet.playerName, packet.playerNames, packet.dead, copyImpostor && packet.impostor, packet.data, packet.position);
    }

}
