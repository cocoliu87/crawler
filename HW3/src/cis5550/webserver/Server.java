package cis5550.webserver;


import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import cis5550.tools.Logger;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class Server implements Runnable {
    static Logger log = Logger.getLogger(Server.class);
    final static String SERVER_NAME = "hw-coco";

    static Server server = null;
    static Server serverSecure = null;
    static boolean threadLaunched = false;

    static List<Routing> rt = new ArrayList<Routing>();

    static int port = 80;
    static int sPort = 443;
    static String path = "";

    public final boolean secure;

    volatile Routing routing;
    volatile ConcurrentHashMap<String, Session> sessions;
    public Server(boolean secure) {
        this.secure = secure;
        sessions = new ConcurrentHashMap<>();
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
        HttpsServer httpsServer = null;
        if (!secure) {
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            server.createContext("/", new CISHttpHandler(path, this));
        } else {
            try {
                httpsServer = HttpsServer.create(new InetSocketAddress(sPort), 0);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            SSLContext sslContext = null;
            try {
                sslContext = getSslContext();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));

            httpsServer.createContext("/", new CISHttpHandler(path, this));
        }

        try {
            // TODO: if seeing degraded performance, considering use newCachedThreadPool() instead.
            if (!secure) {
                server.setExecutor(Executors.newFixedThreadPool(NUM_WORKERS));
                log.info("Starting HTTP server...");
                server.start();
            } else {
                httpsServer.setExecutor(Executors.newFixedThreadPool(NUM_WORKERS));
                log.info("Starting HTTP server...");
                httpsServer.start();
            }
        } catch (Exception e) {
            log.error("Starting with multi threading failed", e);
            throw new RuntimeException(e);
        }
    }

    private SSLContext getSslContext() throws Exception {
        String pwd = "secret";
        String keyFile = "keystore.jks";
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(keyFile), pwd.toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, pwd.toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return sslContext;
    }

    public static void port(int N) {
        port = N;
    }
    public static void securePort(int N) {
        sPort = N;
    }

    public static class staticFiles {
        public static void location(String s) {
            path = s;

        }
    }

    public static void get(String s, Route r) {
        if (server == null) {
            serverSecure = new Server(true);
            server = new Server(false);
        }

        Routing routing = new Routing(Routing.Verb.GET, s, r);
        rt.add(routing);
        server.setRouting(routing);
        serverSecure.setRouting(routing);

        if (!threadLaunched) {
            Thread t1 = new Thread(server);
            t1.start();
            Thread t2 = new Thread(serverSecure);
            t2.start();
            startCleanupTread(server.sessions);
            startCleanupTread(serverSecure.sessions);
            threadLaunched = true;
        }
    }

    public static void post(String s, Route r){
        if (server == null) {
            serverSecure = new Server(true);
            server = new Server(false);
        }

        Routing routing = new Routing(Routing.Verb.POST, s, r);
        rt.add(routing);
        server.setRouting(routing);
        serverSecure.setRouting(routing);

        if (!threadLaunched) {
            Thread t1 = new Thread(server);
            t1.start();
            Thread t2 = new Thread(serverSecure);
            t2.start();
            startCleanupTread(server.sessions);
            startCleanupTread(serverSecure.sessions);
            threadLaunched = true;
        }
    }

    public static void put(String s, Route r){
        if (server == null) {
            serverSecure = new Server(true);
            server = new Server(false);
        }

        Routing routing = new Routing(Routing.Verb.PUT, s, r);
        rt.add(routing);
        server.setRouting(routing);
        serverSecure.setRouting(routing);

        if (!threadLaunched) {
            Thread t1 = new Thread(server);
            t1.start();
            Thread t2 = new Thread(serverSecure);
            t2.start();
            startCleanupTread(server.sessions);
            startCleanupTread(serverSecure.sessions);
            threadLaunched = true;
        }
    }

    private static void startCleanupTread(ConcurrentHashMap<String, Session> sessions) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    System.out.println("periodic checking...");
                    try {
                        // every 10 minutes, clean up the session table.
                        Thread.sleep(600*1000);
                    } catch (InterruptedException e) {
                        System.out.println(e.toString());
                        throw new RuntimeException(e);
                    }
                    for (Map.Entry<String, Session> entry : sessions.entrySet()) {
                        if (entry.getKey() != null) {
                            SessionImpl s = (SessionImpl)entry.getValue();
                            long now = System.currentTimeMillis();
                            if (s.getLastAccessedTime() < (now - (s.getAvailableInSecond()*1000L))) {
                                sessions.remove(entry.getKey());
                            }
                        }
                    }
                }
            }
        }).start();
    }

    public static void before(Condition c, Request req, Response resp) {
        c.operate(req, resp);
    }

    public static void after(Condition c, Request req, Response resp) {
        c.operate(req, resp);
    }
}