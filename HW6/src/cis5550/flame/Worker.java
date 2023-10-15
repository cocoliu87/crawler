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
            while (rows.hasNext()) {
                Row r = rows.next();
                FlamePair pair = lambda.op(r.get("value"));
                if (pair != null) {
                    Row newRow = new Row(pair.a + Delimiter + r.key());
                    newRow.put(r.key(), pair.b);
                    client.putRow(output, newRow);
                    //System.out.println("Writing row: "  + newRow);
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
            while (rows.hasNext()) {
                Row r = rows.next();
                String newAccu = accu;
                if (r != null) {
                    for (String col: r.columns()) {
                        newAccu = lambda.op(accu, r.get(col));
                    }
                    Row newRow = new Row(r.key());
                    newRow.put("value", newAccu);
                    client.putRow(output, newRow);
                }
            }
            return "";
        });
	}
}
