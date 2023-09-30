package cis5550.kvs;

import cis5550.webserver.Server;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.System.exit;

public class Worker extends cis5550.generic.Worker {
    static ConcurrentHashMap<String, Map<String, Row>> tables = new ConcurrentHashMap<>();
    public Worker(String ip, int port, String id) {
        super(ip, port, id);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            exit(1);
        }
        int port = Integer.parseInt(args[0]);
        Server.port(port);
        createFile(args[1]);
        startPingThread(args[2], args[1]+"/id", port);

        cis5550.webserver.Server.port(port);
        put();
        get();
    }
    private static void put() {
        cis5550.webserver.Server.put("/data/:t/:r/:c", (request, response) -> {
            Map<String, String> m = request.params();
            String t = m.get("t");
            String r = m.get("r");
            String c = m.get("c");
            assert t!=null;
            if (tables.containsKey(m.get("t"))) {
                Row row = tables.get(t).get(r);
                row.put(c, request.bodyAsBytes());
            } else {
                Map<String, Row> entry = new HashMap<>();
                Row row = new Row(c);
                row.put(c, request.bodyAsBytes());
                entry.put(r, row);
                tables.put(t, entry);
            }
            response.status(200, "OK");
            return "OK";
        });
    }

    private static void get() {
        cis5550.webserver.Server.get("/data/:t/:r/:c", ((request, response) -> {
            Map<String, String> m = request.params();
            String t = m.get("t");
            String r = m.get("r");
            String c = m.get("c");
            assert t!= null;
            if (!tables.containsKey(t) || !tables.get(t).containsKey(r) || tables.get(t).get(r).get(c) == null) {
                response.status(404, "Not Found");
                return "404 Not Found";
            }
            response.bodyAsBytes(getRow(t, r, c));
            return null;
        }));
    }

    private static byte[] getRow(String t, String r, String c) {
        return tables.get(t).get(r).getBytes(c);
    }

    private static void createFile(String dir) throws IOException {
        File d = new File(dir);
        if (!d.exists()) {
            boolean success = d.mkdirs();
            System.out.println("Creating directory succeeded? "+ success);
        } else {
            for (File f: Objects.requireNonNull(d.listFiles())) {
                f.delete();
            }
            d.delete();
        }
        File f = new File(dir);
        if (!f.exists()) {
            f.mkdirs();
        }
        f = new File(dir+"/id");
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
    }
}
