package cis5550.kvs;

import cis5550.webserver.Server;

public class Coordinator extends cis5550.generic.Coordinator {
    public static void main(String[] args){
        int port = Integer.parseInt(args[0]);
        Server.port(port);
        registerRoutes();
        startCleanupThread();
    }
}