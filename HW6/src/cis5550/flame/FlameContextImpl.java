package cis5550.flame;

import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.HTTP;
import cis5550.tools.Hasher;
import cis5550.tools.Partitioner;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.util.stream.Collectors.joining;

public class FlameContextImpl implements FlameContext {
    StringBuilder builder;
    KVSClient kvsClient;
    String jarName;

    public FlameContextImpl(String name, KVSClient client) {
        this.builder = new StringBuilder();
        this.jarName = name;
        this.kvsClient = client;
    }

    @Override
    public KVSClient getKVS() {
        return kvsClient;
    }

    @Override
    public void output(String s) {
        builder.append(s);
    }

    @Override
    public FlameRDD parallelize(List<String> list) throws Exception {
        String tableName = UUID.randomUUID().toString();
        for (String str : list) {
            Row row = new Row(Hasher.hash(createRowKey()));
            row.put("value", str.getBytes());
            kvsClient.putRow(tableName, row);
        }
        return new FlameRDDImpl(kvsClient, this, tableName);
    }

    public boolean invokeOperation(String ops, byte[] lambda, String inputTable, String outputTable, String accumulator) throws IOException {
        //String outputTable = UUID.randomUUID().toString();
        Partitioner p = new Partitioner();

        int workers = kvsClient.numWorkers();
        for (int i = 0; i < workers; i++) {
            if (i != workers - 1)
                p.addKVSWorker(kvsClient.getWorkerAddress(i), kvsClient.getWorkerID(i), kvsClient.getWorkerID(i+1));
            else {
                p.addKVSWorker(kvsClient.getWorkerAddress(i), null, kvsClient.getWorkerID(0));
                p.addKVSWorker(kvsClient.getWorkerAddress(i), kvsClient.getWorkerID(workers-1), null);
            }
        }

        for (String w: Coordinator.getWorkers()) {
            p.addFlameWorker(w);
        }

        Vector<Partitioner.Partition> pps = p.assignPartitions();
        Thread[] threads = new Thread[pps.size()];
        HTTP.Response[] results = new HTTP.Response[pps.size()];
        for (int i = 0; i < pps.size(); i++) {
            Partitioner.Partition pp = pps.elementAt(i);
            Map<String, String> requestParams = new HashMap<>();
            requestParams.put("input", inputTable);
            requestParams.put("output", outputTable);
            if (pp.fromKey != null) requestParams.put("fromRow", pp.fromKey);
            if (pp.toKeyExclusive != null) requestParams.put("toRow", pp.toKeyExclusive);
            requestParams.put("coordinator", kvsClient.getCoordinator());
            if (accumulator != null) requestParams.put("accu", accumulator);

            String url = requestParams.keySet().stream()
                    .map(key -> {
                        try {
                            return key + "=" + encodeValue(requestParams.get(key));
                        } catch (UnsupportedEncodingException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(joining("&", "http://"+pp.assignedFlameWorker+ops+"?", ""));
/*            String query = "?input=" + inputTable + "&output=" + outputTable + "&fromRow=" + pp.fromKey + "&toRow=" + pp.toKeyExclusive;
            final String url = "http://"+pp.assignedFlameWorker+ops+query;*/
            final int j = i;
            threads[i] = new Thread("flat-map #"+(i+1)) {
                public void run() {
                    try {
                        results[j] = HTTP.doRequest("POST", url, lambda);
                    } catch (Exception e) {
                        results[j] = new HTTP.Response(null, null, 400);
                        e.printStackTrace();
                    }
                }
            };
            threads[i].start();
        }
        for (int i = 0; i < threads.length; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException ie) {
            }
        }

        for (HTTP.Response r: results) {
            if (r.statusCode() >= 300) {
                return false;
            }
            builder.append(new String(r.body(), StandardCharsets.UTF_8));
        }
        return true;
    }

    private String encodeValue(String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String createRowKey() {
        Random rand = new Random();
        return rand.ints(97,123).limit(5).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }
}
