package cis5550.generic;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;

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

    public static void startPingThread(String domain, int port) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(true) {
                    System.out.println("Coordinator is periodically checking...");
                    try {
                        Thread.sleep(5*1000L);
                        String id = randomLetters(5);
                        URL url = new URL(String.format("http://%s/ping?id=%s&port=%d", domain, id, port));
                        System.out.println(url);
                        url.getContent();
                    } catch (InterruptedException | IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }).start();
    }

    public String toString(){
        return this.id+","+this.ip+":"+String.valueOf(this.port);
    }

    private static String randomLetters(int number) {
        StringBuilder s = new StringBuilder();
        char d = 'z' - 'a';
        while (number-- > 0) {
            s.append((char)('a' + Math.random() * d));
        }
        return s.toString();
    }
}
