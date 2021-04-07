package net.iceyleagons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.SneakyThrows;
import net.iceyleagons.VoiceSettings.NoiseSuppression;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class VoiceServer {
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;

    public static Gson gson = new GsonBuilder().create();
    public static VoicePacket.SyncData serverSettings = new VoicePacket.SyncData(48_000, 16, 960 * 10, true, 15.d, new NoiseSuppression[]{NoiseSuppression.NONE, NoiseSuppression.RNNoise});
    private String settings;

    public static double maxDistance = 15.d;

    public Map<String, RemoteClient> playerMap = new HashMap<>();

    public void start() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(8769);

                while (true) {
                    Socket socket = serverSocket.accept();

                    VoicePacket packet = gson.fromJson(new DataInputStream(socket.getInputStream()).readUTF(), VoicePacket.class);
                    if (packet.packetType != VoicePacket.PacketType.SYNC) {
                        // Something really bad happened. Disconnect the client.
                        socket.close();
                    } else executorService.execute(() -> onClientSync(socket, packet));
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
            System.out.println("Syncing player " + packet.playerName);
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
                        VoicePacket packet = gson.fromJson(packetRaw, VoicePacket.class);

                        switch (packet.packetType) {
                            default:
                            case VOICE_SEND:
                                handleVoice(packet);
                                break;
                            case PLAYER_JOIN:
                                // Ignore.
                                break;
                            case PLAYER_LEAVE:
                                onClientDisconnect(packet);
                                disconnect();
                                break;
                            case SYNC:
                                // Something happened that caused the client to try to sync twice.
                            case VOICE_RECEIVE:
                                // Something really bad happened, the client SHOULDN'T be sending VOICE_RECEIVEs towards the server
                                disconnect();
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
            outputStream.writeUTF(gson.toJson(packet));
        }

        public void handleVoice(VoicePacket packet) {
            this.lastKnownPosition = packet.position;
            this.dead = packet.dead;
            this.impostor = packet.impostor;

            broadcast(VoicePacket.PacketType.VOICE_RECEIVE, packet);
        }
    }

    private final boolean debug = false;

    private static final boolean impostorChat = false;

    public void broadcast(VoicePacket.PacketType packetType, VoicePacket originalPacket) {
        executorService.execute(() -> {
            VoicePacket packet = clonePacket(packetType, originalPacket, false);

            if (!debug)
                Arrays.stream(packet.playerNames)
                        .filter(name -> !name.equals(originalPacket.playerName))
                        .filter(playerMap::containsKey)
                        .map(playerMap::get)
                        .filter(client -> packet.dead == client.dead || !packet.dead || !impostorChat && packet.impostor && client.isImpostor())
                        .filter(client -> maxDistance > packet.position.distanceTo(client.lastKnownPosition))
                        .forEach(client -> {
                            if (client.isImpostor() && packet.impostor)
                                client.sendPacket(clonePacket(packetType, originalPacket, true));
                            else client.sendPacket(packet);
                        });
            else playerMap.get(packet.playerName).sendPacket(packet);
        });
    }

    public void recomputeSettings() {
        settings = gson.toJson(serverSettings);
    }

    public VoiceServer() {
        recomputeSettings();
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
