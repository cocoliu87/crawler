package cis5550.generic;

public class Worker {
    int port;
    String ip;
    public Worker(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public static void startPingThread() {

    }

    public String toString(){
        return this.ip+":"+String.valueOf(this.port);
    }

}
