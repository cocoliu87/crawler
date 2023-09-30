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

    public static void startPingThread(String domain, String storage, int port) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(true) {
                    System.out.println("Coordinator is periodically checking...");
                    try {
                        Thread.sleep(5*1000L);
                        File dir = new File(storage);
                        if (!dir.exists()) {
                            boolean success = dir.mkdirs();
                            System.out.println("Creating directory succeeded? "+ success);
                        }
                        File f = new File(storage + "/id.txt");
                        if (!f.exists()) {
                            boolean success = f.createNewFile();
                            System.out.println("Creating id file succeeded? "+ success);
                        }
                        Scanner r = new Scanner(f);
                        String id = "";
                        while (r.hasNextLine()) {
                            id = r.nextLine();
                        }
                        String idReaded = id.isEmpty()? randomLetters(5) : id;
                        FileWriter fw = new FileWriter(f);
                        fw.write(idReaded);
                        fw.close();
                        URL url = new URL(String.format("http://%s/ping?id=%s&port=%d", domain, idReaded, port));
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
