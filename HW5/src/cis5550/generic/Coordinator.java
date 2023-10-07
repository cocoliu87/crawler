package cis5550.generic;

import cis5550.webserver.Request;
import cis5550.webserver.Response;
import cis5550.webserver.Route;
import cis5550.webserver.Server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Coordinator {
    static ConcurrentHashMap<String, Worker> workers = new ConcurrentHashMap<>();;
    public Coordinator() {
    }

    public static List<String> getWorkers() {
        List<String> strs = new ArrayList<>();
        for (Worker w: workers.values()) {
            if (w.lastAccessTime >= System.currentTimeMillis() - w.inActivatedWindow) {
                strs.add(w.toString());
            }
        }
        return strs;
    }

    private static boolean addWorker(Request req) {
        String id = "";
        int port = -1;
        for (String w: req.queryParams()) {
            switch (w) {
                case "id":
                    id = req.queryParams(w);
                    break;
                case "port":
                    port = Integer.parseInt(req.queryParams(w));
                    break;
                default:
                    break;
            }
            if (port >= 0 && !id.isEmpty()) {
                workers.put(id, new Worker(req.ip(), port, id));
            }
        }
        return !id.isEmpty() && port >= 0;
    }

    public static String workerTable() {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>")
                .append("<html>")
                .append("<body>");
        html.append("<table>");
        for (Worker w: workers.values()) {
            String link = "http://"+w.getIp()+":"+w.getPort()+"/";
            html.append("<tr>\n")
                    .append("<td>")
                    .append(w.getId())
                    .append("</td>\n")
                    .append("<td>")
                    .append(w.getIp())
                    .append("</td>\n")
                    .append("<td>")
                    .append(w.getPort())
                    .append("</td>\n")
                    .append("<td>").append("<a href=\"").append(link).append("\">").append(link).append("</a>")
                    .append("</td>\n")
                    .append("</tr>\n");
        }
        html.append("</table>");
        html.append("</body>").append("</html>");
        return html.toString();
    }

    public static void registerRoutes() {
        cis5550.webserver.Server.get("/", (request, response) -> {return workerTable();});
        cis5550.webserver.Server.get("/ping", (request, response) -> {
            boolean success = addWorker(request);
            return success? "OK":"Failed";
        });
        cis5550.webserver.Server.get("/workers", (request, response) -> {
            List<String> workers = getWorkers();
            String ans = workers.size() + "\n";
            return ans + String.join("\n", workers);
        });
    }
    protected static void startCleanupThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    System.out.println("Coordinator is periodically checking...");
                    try {
                        // every minute, clean up the session table.
                        Thread.sleep(1000L);
                    } catch (InterruptedException e) {
                        System.out.println(e.toString());
                        throw new RuntimeException(e);
                    }
                    for (Map.Entry<String, Worker> entry : workers.entrySet()) {
                        if (entry.getKey() != null) {
                            Worker w = entry.getValue();
                            long now = System.currentTimeMillis();
                            if (w.lastAccessTime < (now - w.inActivatedWindow)) {
                                workers.remove(entry.getKey());
                            }
                        }
                    }
                }
            }
        }).start();
    }
}