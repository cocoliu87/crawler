package cis5550.generic;

public class Worker {
    public int getPort() {
        return port;
    }

    public String getIp() {
        return ip;
    }

    public String getId() {
        return id;
    }

    int port;
    String ip;
    String id;
    long lastAccessTime;
    long inActivatedWindow;
    public Worker(String ip, int port, String id) {
        this.ip = ip;
        this.port = port;
        this.id = id;
        this.lastAccessTime = System.currentTimeMillis();
        this.inActivatedWindow = 15 * 1000L;
    }

    public static void startPingThread() {

    }

    public String toString(){
        return this.id+","+this.ip+":"+String.valueOf(this.port);
    }

}
