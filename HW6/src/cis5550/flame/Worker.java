package cis5550.flame;

import java.util.*;
import java.net.*;
import java.io.*;

import static cis5550.webserver.Server.*;
import cis5550.tools.Hasher;
import cis5550.tools.Serializer;
import cis5550.kvs.*;
import cis5550.webserver.Request;

class Worker extends cis5550.generic.Worker {
    static final String Delimiter = "@";
	public static void main(String args[]) {
        if (args.length != 2) {
            System.err.println("Syntax: Worker <port> <coordinatorIP:port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        String server = args[1];
          startPingThread(server, ""+port, port);
        final File myJAR = new File("__worker"+port+"-current.jar");

        port(port);

        post("/useJAR", (request,response) -> {
          FileOutputStream fos = new FileOutputStream(myJAR);
          fos.write(request.bodyAsBytes());
          fos.close();
          return "OK";
        });

        post("/rdd/flatMap", (request, response) -> {
            String input = "", output = "", fromRow = null, toRow = null, coordinator = "";
            for (String key: request.queryParams()) {
                switch (key) {
                    case "input" -> input = request.queryParams(key);
                    case "output" -> output = request.queryParams(key);
                    case "fromRow" -> fromRow = request.queryParams(key);
                    case "toRow" -> toRow = request.queryParams(key);
                    case "coordinator" -> coordinator = request.queryParams(key);
                }
            }

            FlameRDD.StringToIterable lambda = (FlameRDD.StringToIterable) Serializer.byteArrayToObject(request.bodyAsBytes(), myJAR);
            KVSClient client = new KVSClient(coordinator);
            Iterator<Row> rows = client.scan(input, fromRow, toRow);
            while (rows.hasNext()) {
                Row r = rows.next();
                Iterable<String> values = lambda.op(r.get("value"));
                if (values != null) {
                    Iterator<String> iter = values.iterator();
                    while (iter.hasNext()) {
                        Row newRow = new Row(Hasher.hash(FlameContextImpl.createRowKey()));
                        newRow.put("value", iter.next());
                        client.putRow(output, newRow);
                    }
                }
            }
            return "";
        });

        post("/rdd/mapToPair", (request, response) -> {
            String input = "", output = "", fromRow = null, toRow = null, coordinator = "";
            for (String key: request.queryParams()) {
                switch (key) {
                    case "input" -> input = request.queryParams(key);
                    case "output" -> output = request.queryParams(key);
                    case "fromRow" -> fromRow = request.queryParams(key);
                    case "toRow" -> toRow = request.queryParams(key);
                    case "coordinator" -> coordinator = request.queryParams(key);
                }
            }

            FlameRDD.StringToPair lambda = (FlameRDD.StringToPair) Serializer.byteArrayToObject(request.bodyAsBytes(), myJAR);
            KVSClient client = new KVSClient(coordinator);
            Iterator<Row> rows = client.scan(input, fromRow, toRow);
            Map<String, Row> cache = new HashMap<>();
            while (rows.hasNext()) {
                Row r = rows.next();
                FlamePair pair = lambda.op(r.get("value"));
                if (pair != null) {
                    Row row = cache.get(pair.a);
                    if (row == null) {
                        row = new Row(pair.a);
                    }
                    row.put(r.key(), pair.b);
                    cache.put(pair.a, row);
                }
            }
            for (Map.Entry<String, Row> entry: cache.entrySet()) {
                Row row = client.getRow(output, entry.getKey());
                client.putRow(output, combineRows(row, entry.getValue(), fromRow + toRow));
            }
            return "";
        });

        post("/rdd/foldByKey", (request, response) -> {
            String input = "", output = "", fromRow = null, toRow = null, coordinator = "", accu = "";
            for (String key: request.queryParams()) {
                switch (key) {
                    case "input" -> input = request.queryParams(key);
                    case "output" -> output = request.queryParams(key);
                    case "fromRow" -> fromRow = request.queryParams(key);
                    case "toRow" -> toRow = request.queryParams(key);
                    case "coordinator" -> coordinator = request.queryParams(key);
                    case "accu" -> accu = request.queryParams(key);
                }
            }

            FlamePairRDD.TwoStringsToString lambda = (FlamePairRDD.TwoStringsToString) Serializer.byteArrayToObject(request.bodyAsBytes(), myJAR);
            KVSClient client = new KVSClient(coordinator);
            Iterator<Row> rows = client.scan(input, fromRow, toRow);
            Map<String, Row> cache = new HashMap<>();
            while (rows.hasNext()) {
                Row r = rows.next();
                String newAccu = accu;
                if (r != null) {
                    for (String col: r.columns()) {
                        newAccu = lambda.op(newAccu, r.get(col));
                    }
                    Row newRow = new Row(r.key());
                    newRow.put(r.key().split("@")[1], newAccu);
                    cache.put(r.key(), newRow);
                }
            }
            Map<String, Row> aggregated = new HashMap<>();
            for (Row r: cache.values()) {
                String key = r.key().split("@")[0];
                Row row = aggregated.get(key);
                if (row == null) {
                    row = new Row(key);
                }

                for (String c: r.columns()) {
                    row.put(c, r.get(c));
                }
                aggregated.put(key, row);
            }

            Iterator<Row> iter = aggregated.values().iterator();

            while (iter.hasNext()) {
                Row r = iter.next();
                String newAccu = accu;
                if (r != null) {
                    for (String col: r.columns()) {
                        newAccu = lambda.op(newAccu, r.get(col));
                    }
                    Row newRow = new Row(r.key());
                    newRow.put("value", newAccu);
                    client.putRow(output, newRow);
                    System.out.println("FoldByKey -- Input Table: " + input + "; Output Table: " + output + "; Writing row: "  + newRow);
                }
            }

            return "";
        });
	}

    public static Row combineRows(Row r1, Row r2, String extraKey) {
        if (r1 == null && r2 == null) {
            return null;
        }

        if (r1 != null && r2 != null && !r1.key().equals(r2.key()))
            return null;
        String key = r1 == null ? r2.key() : r1.key();
        Row newRow = new Row(key + "@" + extraKey);
        if (r1 != null) {
            for (String c : r1.columns()) {
                newRow.put(c, r1.get(c));
            }
        }
        if (r2 != null) {
            for (String c : r2.columns()) {
                newRow.put(c, r2.get(c));
            }
        }
        return newRow;
    }

}
