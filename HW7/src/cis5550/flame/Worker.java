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

        post("/rdd/flatMapToPair", (request, response) -> {
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

            FlameRDD.StringToPairIterable lambda = (FlameRDD.StringToPairIterable) Serializer.byteArrayToObject(request.bodyAsBytes(), myJAR);
            KVSClient client = new KVSClient(coordinator);
            Iterator<Row> rows = client.scan(input, fromRow, toRow);
            Map<String, Row> cache = new HashMap<>();
            while (rows.hasNext()) {
                Row r = rows.next();
                Iterable<FlamePair> values = lambda.op(r.get("value"));
                if (values != null) {
                    Iterator<FlamePair> iter = values.iterator();
                    while (iter.hasNext()) {
                        FlamePair fp = iter.next();
                        String unique = UUID.randomUUID().toString().split("-")[0];
                        client.put(output, fp.a, unique, fp.b);
                    }
                }
            }

            System.out.println("Writing into output table " + output);

            return "";
        });

        post("/pairRdd/flatMap", (request, response) -> {
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

            FlamePairRDD.PairToStringIterable lambda = (FlamePairRDD.PairToStringIterable) Serializer.byteArrayToObject(request.bodyAsBytes(), myJAR);
            KVSClient client = new KVSClient(coordinator);
            Iterator<Row> rows = client.scan(input, fromRow, toRow);
            while (rows.hasNext()) {
                Row r = rows.next();
                List<Iterable<String>> pairs = new ArrayList<>();
                for (String c: r.columns()) {
                    FlamePair fp = new FlamePair(c, r.get(c));
                    Iterable<String> values = lambda.op(fp);
                    pairs.add(values);
                }

                for (Iterable<String> iter: pairs) {
                    Row newRow = new Row(r.key());
                    client.putRow(output, newRow);
                }
            }
            return "";
        });

        post("/pairRDD/join", (request, response) -> {
            String input = "", output = "", fromRow = null, toRow = null, coordinator = "", joinTo = "";
            for (String key: request.queryParams()) {
                switch (key) {
                    case "input" -> input = request.queryParams(key);
                    case "output" -> output = request.queryParams(key);
                    case "fromRow" -> fromRow = request.queryParams(key);
                    case "toRow" -> toRow = request.queryParams(key);
                    case "coordinator" -> coordinator = request.queryParams(key);
                    case "joinTo" -> joinTo = request.queryParams(key);
                }
            }

            KVSClient client = new KVSClient(coordinator);
            Iterator<Row> t1Rows = client.scan(input, fromRow, toRow);
            Iterator<Row> t2Rows = client.scan(joinTo, fromRow, toRow);
            Map<String, Row> t1Map = new HashMap<>(), t2Map = new HashMap<>();
            while (t1Rows.hasNext()) {
                Row r = t1Rows.next();
                t1Map.put(r.key(), r);
            }
            while (t2Rows.hasNext()) {
                Row r = t2Rows.next();
                t2Map.put(r.key(), r);
            }

            for (Map.Entry<String, Row> entry: t1Map.entrySet()) {
                if (t2Map.containsKey(entry.getKey())) {
                   client.putRow(output, joinRows(entry.getValue(), t2Map.get(entry.getKey())));
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
            while (rows.hasNext()) {
                Row r = rows.next();
                FlamePair pair = lambda.op(r.get("value"));
                if (pair != null) {
                    client.put(output, pair.a, r.key(), pair.b);
                }
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
                    client.put(output, r.key(), "value", newAccu);
                }
            }

            return "";
        });

        post("/rdd/fromTable", (request, response) -> {
            String input = "", output = "", fromRow = null, toRow = null, coordinator = "";
            for (String key: request.queryParams()) {
                switch (key) {
                    case "input" -> input = request.queryParams(key);
                    case "output" -> output = request.queryParams(key);
                    case "coordinator" -> coordinator = request.queryParams(key);
                }
            }

            FlameContext.RowToString lambda = (FlameContext.RowToString) Serializer.byteArrayToObject(request.bodyAsBytes(), myJAR);
            KVSClient client = new KVSClient(coordinator);
            Iterator<Row> rows = client.scan(input);
            while (rows.hasNext()) {
                Row oriRow = rows.next();
                Row newRow = new Row(oriRow.key());
                newRow.put("value", lambda.op(oriRow));
                client.putRow(output, newRow);
            }
            return "";
        });
	}

    /**
     * Returns a Row object that is joined (combinations) columns'
     * value. If both rows don't have a same key, return null.
     *
     * @param  r1 the first row
     * @param  r2 the second row
     * @return a new row object with all combinations of columns value
     */
    public static Row joinRows(Row r1, Row r2) {
        if (!r1.key().equals(r2.key())) return null;

        Row joinedRow = new Row(r1.key());
        for (String c1: r1.columns()) {
            for (String c2: r2.columns()) {
                joinedRow.put(c1+"@c"+c2, r1.get(c1)+","+r2.get(c2));
            }
        }
        return joinedRow;
    }
}
