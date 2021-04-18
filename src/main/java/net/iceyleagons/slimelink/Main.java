package net.iceyleagons.slimelink;

import lombok.AllArgsConstructor;
import net.iceyleagons.slimelink.server.ServerUtils;
import net.iceyleagons.slimelink.server.ServerUtils.RemoteClient;
import net.iceyleagons.slimelink.server.UDPServer;
import net.iceyleagons.slimelink.server.WSServer;
import org.rapidoid.annotation.Valid;
import org.rapidoid.setup.My;
import org.rapidoid.setup.On;

import java.net.InetSocketAddress;

public class Main {

    public static int basePort = 8769;

    public static void main(String[] args) {
        new WSServer(new InetSocketAddress(basePort + 1)).start();
        new UDPServer(basePort);

        On.port(8081);

        My.errorHandler(((req, resp, error) -> resp.code(404)));

        On.get("/users").plain(String.valueOf(ServerUtils.clientMap.size()));
        On.get("/user").json((@Valid String user) -> {
            if (ServerUtils.clientMap.containsKey(user)) {
                RemoteClient<?> client = ServerUtils.clientMap.get(user);
                return ServerUtils.gson.toJson(new RequestObject(client.getPlayers(), client.isMuted(), client.isDeafened()));
            }
            return "{}";
        });
    }

    @AllArgsConstructor
    public static class RequestObject {
        public String[] players;
        public boolean muted, deafened;
    }

}
