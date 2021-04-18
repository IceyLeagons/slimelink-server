package net.iceyleagons.slimelink;

import lombok.*;
import net.iceyleagons.slimelink.server.ServerUtils;
import net.iceyleagons.slimelink.utils.GZIPUtils;
import net.iceyleagons.slimelink.VoiceSettings.NoiseSuppression;
import org.java_websocket.WebSocket;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Base64;

@AllArgsConstructor
@RequiredArgsConstructor
public class VoicePacket {

    @NonNull
    public final String userId;
    @NonNull
    public final PacketType packetType;
    // The name of the sender.
    // Once registered (with the SYNC packet) this becomes irrelevant. (may be used for debugging purposes)
    @NonNull
    public final String playerName;
    // Whether or not the player is dead. If they are dead, only dead players can hear them
    // otherwise both dead and alive players (within range) can hear them.
    @NonNull
    public final boolean dead;
    // Impostor chat.
    @NonNull
    public final boolean impostor;

    // The data of the packet. May contain SYNC data, or signed PCM-16 at 48kHz encoded with base64.
    public String data;
    // The position of this player. Is used for the proximity function of the mod.
    public Position position;

    public VoicePacket(String userId, PacketType packetType, String playerName, boolean dead, boolean impostor, byte[] data, Position position) {
        this.userId = userId;
        this.packetType = packetType;
        this.playerName = playerName;
        this.dead = dead;
        this.impostor = impostor;
        this.data = Base64.getEncoder().encodeToString(data);
        this.position = position;
    }

    public void send(OutputStream stream) throws IOException {
        stream.write(compress());
    }

    @SneakyThrows
    public void send(DatagramSocket socket, InetAddress address, int port) {
        byte[] data = compress();
        socket.send(new DatagramPacket(data, data.length, address, port));
    }

    public void send(WebSocket socket) {
        socket.send(compress());
    }

    public byte[] compress() {
        return GZIPUtils.gzipCompress(toString().getBytes());
    }

    @Override
    public String toString() {
        return ServerUtils.gson.toJson(this);
    }

    @AllArgsConstructor
    @Data
    public static class Position {
        double x, y, z;

        public double dot(Position other) {
            return x * other.x + y * other.y + z * other.z;
        }

        public double getAngle(Position other) {
            /*// double angle = Math.toDegrees(Math.atan2(target.y - y, target.x - x));
            double dot = Doubles.constrainToRange(dot(other) / (length() * other.length()), -1.0, 1.0);

            return Math.acos(dot);*/
            return 0;
        }

        public double distanceTo(Position otherPoint) {
            return Math.sqrt(Math.pow(x - otherPoint.x, 2) + Math.pow(z - otherPoint.z, 2));
        }
    }

    public enum PacketType {
        READY, CLIENT_LEAVE, SYNC, VOICE, STATE
    }

    @AllArgsConstructor
    public static class StateData {
        public boolean muted;
        public boolean deafened;

        public String[] players;
    }

    @AllArgsConstructor
    public static class SyncData {
        public String userId;

        public float sampleRate;
        public int bytes;
        public int bufferLength;
        public boolean mono;
        public double hearingRange;
        // Why is this here? Because RNNoise is a bit weird, and it works on arrays with a size that's a multiple of 960.
        // So, supporting servers will have to have an array that has a size of 960*n
        public NoiseSuppression[] supportedNoiseSuppression;

        public int opusFrameSize;
        public int opusBitrate;
    }

}
