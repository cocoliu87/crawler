package cis5550.kvs;

import cis5550.tools.Logger;
import cis5550.webserver.Request;
import cis5550.webserver.Response;
import cis5550.webserver.Server;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.System.exit;

public class Worker extends cis5550.generic.Worker {
    static ConcurrentHashMap<String, Map<String, Row>> tables = new ConcurrentHashMap<>();
    public Worker(String ip, int port, String id) {
        super(ip, port, id);
    }

    static final Logger logger = Logger.getLogger(Worker.class);

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
        cis5550.webserver.Server.put("/data/:t/:r/:c", Worker::addEntryToTables);
    }

    private static void get() {
        cis5550.webserver.Server.get("/data/:t/:r/:c", (Worker::getFromTables));
        cis5550.webserver.Server.get("/", (Worker::listTablesName));
        cis5550.webserver.Server.get("/view/:table", (Worker::viewTable));
        cis5550.webserver.Server.get("/data/:t/:r", (Worker::getFromTables));
        cis5550.webserver.Server.get("/data/:t", (Worker::getFromTables));
    }

    private static String addEntryToTables(Request request, Response response) {
        Map<String, String> m = request.params();
        String t = m.get("t");
        String r = m.get("r");
        String c = m.get("c");
        assert t!=null;
        if (tables.containsKey(m.get("t"))) {
            Row row = tables.get(t).get(r);
            if (row != null) {
                row.put(c, request.bodyAsBytes());
            } else {
                Map<String, Row> table = tables.get(t);
                row = new Row(c);
                row.put(c, request.bodyAsBytes());
                table.put(r, row);
            }
        } else {
            Map<String, Row> entry = new HashMap<>();
            Row row = new Row(r);
            row.put(c, request.bodyAsBytes());
            entry.put(r, row);
            tables.put(t, entry);
        }

        if (t.startsWith("pt-")) {
            String dir = "__worker" + File.separator + t;
            File tf = new File(dir);
            if (!tf.exists()) {
                boolean success = tf.mkdirs();
                if (!success) {
                    logger.error("create pt directory failed. path is "+tf.getPath());
                }
            }

            for (Map.Entry<String, Row> entry: tables.get(t).entrySet()) {
                if (entry.getKey() != null) {
                    File rf = new File(dir + File.separator + entry.getKey());
                    try {
                        if (rf.exists()) {
                            rf.delete();
                        }
                        rf.createNewFile();
                        FileOutputStream outputStream = new FileOutputStream(rf);
                        Row row = entry.getValue();
                        outputStream.write(row.toByteArray());
                        outputStream.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        response.status(200, "OK");
        return "OK";
    }

    private static String viewTable(Request request, Response response) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n")
                .append("<html>\n")
                .append("<body>\n");
        String tableName = request.params("table");
        html.append("<h1>").append(tableName).append("</h1>\n");
        html.append("<table>\n");
        boolean addHeader = false;
        for (Map.Entry<String, Row> entry: tables.get(tableName).entrySet()) {
            if (entry.getKey() != null) {
                if (!addHeader) {
                    html.append("<tr>\n<th>").append("RowID\n").append("</th>\n");
                    for (String c: entry.getValue().columns()) {
                        html.append("<th>").append(c).append("/th\n");
                    }
                    addHeader = true;
                }

                html.append("<tr>\n")
                    .append("<td>")
                    .append(entry.getKey())
                    .append("</td>\n");
                for (String c: entry.getValue().columns()) {
                        html.append("<td>")
                            .append(new String(entry.getValue().getBytes(c), StandardCharsets.UTF_8))
                            .append("</td>\n");
                }
                html.append("</tr>\n");
            }
        }
        html.append("</table>\n");
        html.append("</body>\n").append("</html>");
        return html.toString();
    }

    private static String listTablesName(Request request, Response response) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n")
                .append("<html>\n")
                .append("<body>\n");
        html.append("<table>\n");
        for (String name: tables.keySet()) {
            html.append("<tr>\n")
                    .append("<td>\n")
                    .append(name)
                    .append("</td>\n")
                    .append("</tr>\n");
        }
        html.append("</table>\n");
        html.append("</body>\n").append("</html>");
        return html.toString();
    }

    private static String getFromTables(Request request, Response response) throws IOException {
        Map<String, String> m = request.params();
        String t = "", r = "", c = "";
        if (m.containsKey("t")) t = m.get("t");
        if (m.containsKey("r")) r = m.get("r");
        if (m.containsKey("c")) c = m.get("c");

        if (!t.isEmpty() && !r.isEmpty() && !c.isEmpty()) {
            if (!t.startsWith("pt-") && (!tables.containsKey(t) || !tables.get(t).containsKey(r) || tables.get(t).get(r).get(c) == null)) {
                response.status(404, "Not Found");
                return "404 Not Found";
            }
        }
        response.bodyAsBytes(getRow(t, r, c));
        return null;
    }

    private static byte[] getRow(String t, String r, String c) throws IOException {
        return t.startsWith("pt-")? getRowFromFile(t, r) : c.isEmpty()? tables.get(t).get(r).toByteArray() : tables.get(t).get(r).getBytes(c);
    }

    private static byte[] getRowFromFile(String t, String r) throws IOException {
        String dir = "__worker" + File.separator + t + File.separator + r;
        File tf = new File(dir);
        byte[] bytes = null;
        if (!tf.isFile() || !tf.exists() || !tf.canRead()) {
            logger.error("the required file is not valid. file path is " + tf.getPath());
            return null;
        } else {
            FileInputStream inputStream = new FileInputStream(tf);
            bytes = inputStream.readAllBytes();
            inputStream.close();
        }
        assert bytes != null;
        String info = new String(bytes, StandardCharsets.UTF_8);
        String[] cols = info.split("\\s+");
        return cols[cols.length-1].getBytes();
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
