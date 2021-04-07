package net.iceyleagons;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.iceyleagons.VoiceSettings.NoiseSuppression;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;

@AllArgsConstructor
@RequiredArgsConstructor
public class VoicePacket {

    @NonNull
    public final PacketType packetType;
    // The name of the sender.
    // Once registered (with the SYNC packet) this becomes irrelevant. (may be used for debugging purposes)
    @NonNull
    public final String playerName;
    // This'll contain the name of the people we wish to send this packet to.
    @NonNull
    public final String[] playerNames;
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

    public VoicePacket(PacketType packetType, String playerName, String[] playerNames, boolean dead, boolean impostor, byte[] data, Position position) {
        this.packetType = packetType;
        this.playerName = playerName;
        this.playerNames = playerNames;
        this.dead = dead;
        this.impostor = impostor;
        this.data = Base64.getEncoder().encodeToString(data);
        this.position = position;
    }

    public void send(DataOutputStream stream) throws IOException {
        stream.writeUTF(VoiceServer.gson.toJson(this));
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
            return Math.sqrt(Math.pow(x - otherPoint.x, 2) + Math.pow(y - otherPoint.y, 2) + Math.pow(z - otherPoint.z, 2));
        }
    }

    public enum PacketType {
        PLAYER_JOIN, PLAYER_LEAVE, SYNC, VOICE_SEND, VOICE_RECEIVE
    }

    @AllArgsConstructor
    public static class SyncData {
        public float sampleRate;
        public int bytes;
        public int bufferLength;
        public boolean mono;
        public double hearingRange;
        // Why is this here? Because RNNoise is a bit weird, and it works on arrays with a size that's a multiple of 960.
        // So, supporting servers will have to have an array that has a size of 960*n
        public NoiseSuppression[] supportedNoiseSuppression;
    }

}