package net.iceyleagons;

import lombok.Data;

import javax.sound.sampled.Mixer;

@Data
public class VoiceSettings {
    // Synced with the server

    private float sampleRate = 8000f;
    private int bytes = 8;
    private int bufferLength = 500;
    private boolean mono = false;
    private double hearingRange = 15.d;

    // ----- //

    // Changeable by the user

    private NoiseSuppression noiseSuppression = NoiseSuppression.NONE;

    private ActivationType activationType = ActivationType.CONTINUOUS;

    private Mixer output;
    private Mixer input;

    private String server;
    private int serverPort;

    // ----- //

    // Statics

    private static VoiceSettings instance;

    // ----- //

    // Types

    public enum NoiseSuppression {
        RNNoise, NONE
    }

    public enum ActivationType {
        PUSH_TO_TALK, PUSH_TO_MUTE, VOICE_ACTIVITY, CONTINUOUS
    }

    public static VoiceSettings getInstance() {
        if(instance == null)
            instance = new VoiceSettings();
        return instance;
    }

}
