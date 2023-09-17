package cis5550.webserver;


import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import cis5550.tools.Logger;
import com.sun.net.httpserver.HttpServer;

import static java.lang.System.exit;

public class Server {
    static Logger log = Logger.getLogger(Server.class);
    final static String SERVER_NAME = "hw-coco";

    static int port = 80;
    public Server() {
    }

    static final int NUM_WORKERS = 100;
    public static void main(String[] args) throws Exception {
        String path = "";
        if (args.length == 2) {
            port = Integer.parseInt(args[0]);
            path = args[1];
        } else {
            System.out.println("Written by Yingqiu Liu");
            exit(0);
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new CISHttpHandler(path));
        try {
            // TODO: if seeing degraded performance, considering use newCachedThreadPool() instead.
            server.setExecutor(Executors.newFixedThreadPool(NUM_WORKERS));
            log.info("Starting HTTP server...");
            server.start();
        } catch (Exception e) {
            log.error("Starting with multi threading failed", e);
            throw new RuntimeException(e);
        }
    }

    public static void port(int N) {
        port = N;
    }

    public static class staticFiles {
        public static void location(String s) {

        }
    }

    public static void get(String s, Route r) {}

    public static void post(String s, Route r){}

    public static void put(String s, Route r){}
}