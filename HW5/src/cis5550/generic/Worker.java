package cis5550.generic;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;
import java.util.Scanner;

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

    public static void startPingThread(String domain, String path, int port) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(true) {
                    System.out.println("Coordinator is periodically checking...");
                    try {
                        Thread.sleep(5*1000L);

                        File f = new File(path);
                        while (!f.exists()) {
//                            boolean success = f.createNewFile();
                            System.out.println("Waiting for file: "+path);
                            Thread.sleep(100L);
                        }
                        Scanner r = new Scanner(f);
                        String id = "";
                        while (r.hasNextLine()) {
                            id = r.nextLine();
                        }
//                        String idReaded = id.isEmpty()? randomLetters(5) : id;
                        FileWriter fw = new FileWriter(f);
                        fw.write(id);
                        fw.close();
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

    protected static String randomLetters(int number) {
        StringBuilder s = new StringBuilder();
        char d = 'z' - 'a';
        while (number-- > 0) {
            s.append((char)('a' + Math.random() * d));
        }
        return s.toString();
    }
}
