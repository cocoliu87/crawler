package cis5550.webserver;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import cis5550.tools.Logger;
import com.sun.net.httpserver.HttpServer;

public class Server implements Runnable {
    static Logger log = Logger.getLogger(Server.class);
    final static String SERVER_NAME = "hw-coco";

    static Server server = null;
    static boolean threadLaunched = false;

    static List<Routing> rt = new ArrayList<Routing>();

    static int port = 80;
    static String path = "";

    volatile Routing routing;
    public Server() {
    }

    public synchronized Routing getRouting() {
        return routing;
    }

    public synchronized void setRouting(Routing r) {
        this.routing = r;
    }

    static final int NUM_WORKERS = 100;
    public void run() {
        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        server.createContext("/", new CISHttpHandler(path, this));
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
            path = s;

        }
    }

    public static void get(String s, Route r) {
        if (server == null) {
            server = new Server();
        }

        Routing routing = new Routing(Routing.Verb.GET, s, r);
        rt.add(routing);
        server.setRouting(routing);

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

        Routing routing = new Routing(Routing.Verb.POST, s, r);
        rt.add(routing);
        server.setRouting(routing);

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

        Routing routing = new Routing(Routing.Verb.PUT, s, r);
        rt.add(routing);
        server.setRouting(routing);

        if (!threadLaunched) {
            Thread t = new Thread(server);
            t.start();
            threadLaunched = true;
        }
    }

    public static void before(Condition c, Request req, Response resp) {
        c.operate(req, resp);
    }

    public static void after(Condition c, Request req, Response resp) {
        c.operate(req, resp);
    }
}