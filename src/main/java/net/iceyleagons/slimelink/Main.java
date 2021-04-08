package net.iceyleagons.slimelink;

import net.iceyleagons.slimelink.server.VoiceServer;
import net.iceyleagons.slimelink.server.WSServer;

import java.net.InetSocketAddress;

public class Main {

    public static int basePort = 8769;

    public static void main(String[] args) {
        new WSServer(new InetSocketAddress(basePort)).start();
        new VoiceServer(basePort + 1).start();
    }

}