package cis5550.kvs;

import cis5550.tools.Logger;
import cis5550.webserver.Request;
import cis5550.webserver.Response;
import cis5550.webserver.Server;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
        cis5550.webserver.Server.put("/rename/:t", Worker::renameTable);
        cis5550.webserver.Server.put("/delete/:t", Worker::deleteTable);
    }

    private static void get() {
        cis5550.webserver.Server.get("/data/:t/:r/:c", (Worker::getFromTables));
        cis5550.webserver.Server.get("/", (Worker::listTablesName));
        cis5550.webserver.Server.get("/view/:table", (Worker::viewTable));
        cis5550.webserver.Server.get("/data/:t/:r", (Worker::getFromTables));
        cis5550.webserver.Server.get("/data/:t", (Worker::getFromTables));
        cis5550.webserver.Server.get("/count/:t", (Worker::getRowCountFromTables));
    }

    private static String deleteTable(Request request, Response response) {
        Map<String, String> m = request.params();
        String t = m.get("t");
        if (t == null) {
            response.status(404, "Not Found");
            return "404 Not Found";
        }
        if (t.startsWith("pt-")) {
            String dir = "__worker" + File.separator + t;
            File tf = new File(dir);
            if (!tf.exists() || !tf.isDirectory()) {
                response.status(404, "Not Found");
                return "404 Not Found";
            } else {
                deleteDirectory(tf);
            }
        } else {
            Map<String, Row> oriTable = tables.get(t);
            if (oriTable != null) {
                System.out.println("Removing table: " + t);
                tables.remove(t);
            } else {
                response.status(404, "Not Found");
                return "404 Not Found";
            }
        }
        response.status(200, "OK");
        return "OK";
    }

    private static boolean deleteDirectory(File target) {
        File[] files = target.listFiles();
        if (files != null) {
            for (File f: files) {
                deleteDirectory(f);
            }
        }
        return target.delete();
    }

    private static String renameTable(Request request, Response response) throws IOException {
        Map<String, String> m = request.params();
        String t = m.get("t");
        if (t == null) {
            response.status(404, "Not Found");
            return "404 Not Found";
        }
        if (t.startsWith("pt-")) {
            String dir = "__worker" + File.separator + t;
            File tf = new File(dir);
            if (!tf.exists() || !tf.isDirectory()) {
                response.status(404, "Not Found");
                return "404 Not Found";
            } else {
                String newFileDir = "__worker" + File.separator + request.body();
                Path src = Paths.get(dir), dst = Paths.get(newFileDir);
                Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            Map<String, Row> oriTable = tables.get(t);
            if (oriTable != null) {
                tables.put(request.body(), oriTable);
                tables.remove(t);
            } else {
                response.status(404, "Not Found");
                return "404 Not Found";
            }
        }
        response.status(200, "OK");
        return "OK";
    }

    private static String getRowCountFromTables(Request request, Response response) {
        Map<String, String> m = request.params();
        String t = m.get("t");
        assert t != null;
        int count = 0;
        if (t.startsWith("pt-")) {
            String dir = "__worker" + File.separator + t;
            File tf = new File(dir);
            if (tf.isDirectory()) {
                count = Objects.requireNonNull(tf.list()).length;
            }
        } else {
            if (tables.containsKey(t)) {
                count = tables.get(t).size();
            }
        }
        return String.valueOf(count);
    }

    private static String addEntryToTables(Request request, Response response) {
        Map<String, String> m = request.params();
        String t = m.get("t");
        String r = m.get("r");
        String c = m.get("c");
        assert t!=null;
        if (tables.containsKey(t)) {
            Row row = tables.get(t).get(r);
            if (row != null) {
                row.put(c, request.bodyAsBytes());
            } else {
                Map<String, Row> table = tables.get(t);
                row = new Row(r);
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
        String tableName = request.params("table");
        if (tables.get(tableName) == null) {
            response.status(404, "Not Found");
            return "Not Found";
        }
        return viewTableWithPagination(request, response);
/*        Set<String> params = request.queryParams();
        if (params != null && !params.isEmpty()) {
            return viewTableWithPagination(request, response);
        } else {
            return viewTableWithoutPagination(request, response);
        }*/
    }

    private static String viewTableWithPagination(Request request, Response response) {
        String tableName = request.params("table");
        TreeMap<String, Row> sortedTable = new TreeMap<>(tables.get(tableName));
        String from = request.queryParams("fromRow");
        int fromRow = from == null || from.isEmpty() || Integer.parseInt(from) < 0 ? 0 : Integer.parseInt(from);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n")
                .append("<html>\n")
                .append("<body>\n");
        html.append("<h1>").append(tableName).append("</h1>\n");
        html.append("<table>\n");
        boolean addHeader = false;

        int idx = -1;
        for (Map.Entry<String, Row> entry: sortedTable.entrySet()) {
            idx++;
            if (idx < fromRow) {
                continue;
            }

            if (idx >= fromRow + 10) {
                break;
            }

            if (entry.getKey() != null) {
                if (!addHeader) {
                    html.append("<tr>\n<th>").append("RowID\n").append("</th>\n");
                    for (String c: entry.getValue().columns()) {
                        html.append("<th>").append(c).append("</th>\n");
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
        if (fromRow + 10 <= sortedTable.size()) {
            String url = request.url() + "?fromRow=" + (fromRow+10);
            html.append("<a href=\"").append(url).append("\">").append("Next").append("</a>");
        }
        html.append("</body>\n").append("</html>");
        return html.toString();
    }
    private static String viewTableWithoutPagination(Request request, Response response) {
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
                        html.append("<th>").append(c).append("</th>\n");
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

        if (!t.startsWith("pt-")) {
            if (t.isEmpty()) {
                response.status(400, "Bad Request");
                return "400 Bad Request";
            }
            if (!tables.containsKey(t)) {
                response.status(404, "Not Found");
                return "404 Not Found";
            }

            if (!r.isEmpty() && !c.isEmpty()) {
                if (!tables.get(t).containsKey(r) || tables.get(t).get(r).get(c) == null) {
                    response.status(404, "Not Found");
                    return "404 Not Found";
                }
            }
        }
        if (r == null || r.isEmpty()) {
            try {
                writeToStream(request, response, t);
            } catch (Exception e) {
                logger.error("writing to stream had error", e);
            }
        } else {
            response.bodyAsBytes(getRow(t, r, c));
        }

        return null;
    }

    private static void writeToStream(Request request, Response response, String t) throws Exception {
        String from = request.queryParams("startRow"), end = request.queryParams("endRowExclusive");
        int fromRow = -1;
        int endRow = -1;
        if (from != null && !from.isEmpty() && end != null && !end.isEmpty())
        {
            fromRow = Integer.parseInt(from);
            endRow = Integer.parseInt(end);
        }
        if (t.startsWith("pt-")) {
            String dir = "__worker" + File.separator + t;
            File tf = new File(dir);
            if (!tf.exists() || !tf.isDirectory()) {
                response.status(404, "Not Found");
                response.body("Not Found");
                return;
            }
            int len = Objects.requireNonNull(tf.listFiles()).length;
            for (File rf: Objects.requireNonNull(tf.listFiles())) {
                len --;
                if (!rf.exists()) continue;
                FileInputStream is = new FileInputStream(rf);
                byte[] bytes = is.readAllBytes();

                if (len == 0) {
                    byte[] newbytes = new byte[bytes.length + 2];
                    System.arraycopy(bytes, 0, newbytes, 0, bytes.length);
                    newbytes[bytes.length] = (byte)10;
                    newbytes[bytes.length+1] = (byte)10;
                    response.write(newbytes);
                } else {
                    byte[] newbytes = new byte[bytes.length + 1];
                    System.arraycopy(bytes, 0, newbytes, 0, bytes.length);
                    newbytes[bytes.length] = (byte)13;
                    response.write(newbytes);
                }
            }
        } else {
            Map<String, Row> table = tables.get(t);
            if (table == null) {
                response.status(404, "Not Found");
                response.body("Not Found");
                return;
            }
            int rows = table.size();
            for (Map.Entry<String, Row> entry: table.entrySet()) {
                rows--;
                byte[] bytes = entry.getValue().toByteArray();
                if (rows == 0) {
                    byte[] newbytes = new byte[bytes.length + 2];
                    System.arraycopy(bytes, 0, newbytes, 0, bytes.length);
                    newbytes[bytes.length] = (byte)10;
                    newbytes[bytes.length+1] = (byte)10;
                    response.write(newbytes);
                } else {
                    byte[] newbytes = new byte[bytes.length + 1];
                    System.arraycopy(bytes, 0, newbytes, 0, bytes.length);
                    newbytes[bytes.length] = (byte)10;
                    response.write(newbytes);
                }
                //response.write(value);
/*                if (rows == 0) {
                    response.write(value.concat("\r\n").getBytes(StandardCharsets.UTF_8));
                } else {
                    response.write(value.concat("\n").getBytes(StandardCharsets.UTF_8));
                }*/
            }
        }
    }

    private static byte[] getRow(String t, String r, String c) throws IOException {
        System.out.println("Trying to get table: " + t);
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
