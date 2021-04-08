package net.iceyleagons.slimelink.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.iceyleagons.slimelink.VoicePacket;
import net.iceyleagons.slimelink.VoiceSettings;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerUtils {

    public static final ExecutorService executorService = Executors.newCachedThreadPool();
    public static final Gson gson = new GsonBuilder().create();
    public static VoicePacket.SyncData serverSettings = new VoicePacket.SyncData(48_000, 16, 960 * 10, true, 15.d, new VoiceSettings.NoiseSuppression[]{VoiceSettings.NoiseSuppression.NONE, VoiceSettings.NoiseSuppression.RNNoise});

    public static final boolean debug = false;

    public static final boolean impostorChat = false;


}
