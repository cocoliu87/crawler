package cis5550.webserver;


import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import cis5550.tools.Logger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import static java.lang.System.exit;

public class Server {
    static Logger log = Logger.getLogger(Server.class);
    public static void main(String[] args) throws Exception {
        int port = 8000;
        String path = "";
        if (args.length == 2) {
            port = Integer.parseInt(args[0]);
            path = args[1];
        } else {
            System.out.println("Written by Yingqiu Liu");
            exit(0);
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new MyHttpHandler(path));
        try {
            // TODO: if seeing degraded performance, considering use newCachedThreadPool() instead.
//            server.setExecutor(Executors.newFixedThreadPool(100));
            server.setExecutor(Executors.newCachedThreadPool());
            log.info("Starting HTTP server...");
            server.start();
        } catch (Exception e) {
            log.error("Starting with multi threading failed", e);
            throw new RuntimeException(e);
        }
    }
}