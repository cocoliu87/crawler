package cis5550.kvs;

import cis5550.webserver.Server;

import static java.lang.System.exit;

public class Worker extends cis5550.generic.Worker {
    public Worker(String ip, int port, String id) {
        super(ip, port, id);
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            exit(1);
        }
        int port = Integer.parseInt(args[0]);
        Server.port(port);
        startPingThread(args[2], port);
    }
}
