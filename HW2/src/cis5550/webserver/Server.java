package cis5550.webserver;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import cis5550.tools.Logger;
import com.sun.net.httpserver.HttpServer;

import static java.lang.System.exit;

public class Server implements Runnable {
    static Logger log = Logger.getLogger(Server.class);
    final static String SERVER_NAME = "hw-coco";

    static Server server = null;
    static boolean threadLaunched = false;

    int port = 80;
    String path = "";
    public Server() {
    }

    static final int NUM_WORKERS = 100;
    public void run() {
//        String path = "";
//        if (args.length == 2) {
//            port = Integer.parseInt(args[0]);
//            path = args[1];
//        } else {
//            System.out.println("Written by Yingqiu Liu");
//            exit(0);
//        }
        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress(this.port), 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        server.createContext("/", new CISHttpHandler(this.path));
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
        if (server != null) {
            server.port = N;
        } else {
            log.error("Server hasn't been initialized for taking port");
        }
    }

    public static class staticFiles {
        public static void location(String s) {
            if (server != null) {
                server.path = s;
            } else {
                log.error("Server hasn't been initialized for taking file path");
            }
        }
    }

    public static void get(String s, Route r) {
        if (server == null) {
            server = new Server();
        }
        if (!threadLaunched) {
            Thread t = new Thread(server);
            t.start();
            threadLaunched = true;
        }
    }

    public static void post(String s, Route r){
        if (server == null) {
            server = new Server();
        }
        if (!threadLaunched) {
            Thread t = new Thread(server);
            t.start();
            threadLaunched = true;
        }
    }

    public static void put(String s, Route r){
        if (server == null) {
            server = new Server();
        }
        if (!threadLaunched) {
            Thread t = new Thread(server);
            t.start();
            threadLaunched = true;
        }
    }
}