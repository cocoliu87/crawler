package cis5550.kvs;


public class Coordinator extends cis5550.generic.Coordinator {
    public static void main(String[] args){
        int port = Integer.parseInt(args[0]);
        cis5550.webserver.Server.port(port);
        registerRoutes();
        startCleanupThread();
    }
}
