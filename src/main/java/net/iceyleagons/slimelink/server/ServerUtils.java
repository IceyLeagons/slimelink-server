package net.iceyleagons.slimelink.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.SneakyThrows;
import net.iceyleagons.slimelink.VoicePacket;
import net.iceyleagons.slimelink.VoiceSettings;
import net.iceyleagons.slimelink.utils.Caesar;
import net.iceyleagons.slimelink.utils.GZIPUtils;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ServerUtils {

    public static final ExecutorService executorService = Executors.newCachedThreadPool();
    public static final Gson gson = new GsonBuilder().create();
    public static VoicePacket.SyncData serverSettings = new VoicePacket.SyncData("usER-id_H3r3",48_000, 16, 960, true, 15.d, new VoiceSettings.NoiseSuppression[]{VoiceSettings.NoiseSuppression.NONE, VoiceSettings.NoiseSuppression.RNNoise}, 960, 12000);

    public static final boolean debug = true;

    public static final boolean impostorChat = false;

    public static final Map<String, RemoteClient<?>> clientMap = new HashMap<>();
    private static final Map<String, Long> clientPacketMap = new HashMap<>();

    static {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                clientPacketMap.entrySet().stream()
                        .filter(entry -> entry.getValue() + 10000 < System.currentTimeMillis())
                        .forEach(entry -> clientMap.remove(entry.getKey()));
            }
        }, 0L, 5000L);
    }

    public static void receivedPacket(String name) {
        clientPacketMap.put(name, System.currentTimeMillis());
    }

    private static String generateId() {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 5;
        Random random = new Random();

        return random.ints(leftLimit, rightLimit + 1)
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    @Getter
    public static class RemoteClient<S> {
        S socket;

        VoicePacket.Position lastKnownPosition = new VoicePacket.Position(0, 0, 0);
        boolean dead = false, impostor = false;

        boolean muted = false, deafened = false;

        String[] players = new String[0];
        String userId;

        int caesar = 0;

        BiConsumer<byte[], Set<S>> sendPacket;
        BiConsumer<RemoteClient<?>, VoicePacket> sendPacketState;
        BiConsumer<RemoteClient<?>, VoicePacket> broadcastPacket;

        Consumer<S> closeConsumer;

        @SneakyThrows
        public RemoteClient(S socket, String caesar, BiConsumer<byte[], Set<S>> sendPacket,
                            BiConsumer<RemoteClient<?>, VoicePacket> broadcastPacket,
                            BiConsumer<RemoteClient<?>, VoicePacket> sendPacketState,
                            Consumer<S> closeConsumer) {
            this.socket = socket;
            this.sendPacket = sendPacket;
            this.broadcastPacket = broadcastPacket;
            this.sendPacketState = sendPacketState;

            this.userId = generateId();

            this.closeConsumer = closeConsumer;

            try {
                if (caesar != null)
                    this.caesar = Integer.parseInt(caesar);
            } catch (NumberFormatException ignored) {
                disconnect();
            }
        }

        @SneakyThrows
        public void disconnect() {
            closeConsumer.accept(socket);
            socket = null;
        }

        @SneakyThrows
        public void sendPacket(VoicePacket packet) {
            if (caesar != 0)
                sendPacket.accept(GZIPUtils.gzipCompress(Caesar.encrypt(packet.toString(), caesar).getBytes()), Collections.singleton(socket));
            else sendPacket.accept(packet.compress(), Collections.singleton(socket));
        }

        public void handleState(VoicePacket packet) {
            if(!packet.userId.equals(userId))
                return;

            this.players = ServerUtils.gson.fromJson(packet.data, VoicePacket.StateData.class).players;
            sendPacketState.accept(this, packet);
        }

        public void handleVoice(VoicePacket packet) {
            if(!packet.userId.equals(userId))
                return;

            this.lastKnownPosition = packet.position;
            this.dead = packet.dead;
            this.impostor = packet.impostor;

            broadcastPacket.accept(this, packet);
        }
    }

}
