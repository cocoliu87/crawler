package cis5550.flame;

import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Hasher;
import cis5550.tools.Serializer;

import java.io.IOException;
import java.util.*;

public class FlameRDDImpl implements FlameRDD{
    String tableName;
    KVSClient client;
    FlameContextImpl ctx;
    public FlameRDDImpl(KVSClient client, FlameContextImpl ctx, String tableName) {
        this.client = client;
        this.ctx = ctx;
        this.tableName = tableName;
    }

    @Override
    public List<String> collect() throws Exception {
        List<String> strs = new ArrayList<>();
        if (!tableName.isEmpty() && client != null) {
            Iterator<Row> iter = client.scan(tableName);

            while (iter.hasNext()) {
                Row r = iter.next();
                for (String c: r.columns()) {
                    strs.add(r.get(c));
                }
            }
        }
        return strs;
    }

    @Override
    public FlameRDD flatMap(StringToIterable lambda) throws Exception {
        String outputTable = UUID.randomUUID().toString();
        this.ctx.invokeOperation("/rdd/flatMap", Serializer.objectToByteArray(lambda), tableName, outputTable, null);
        return new FlameRDDImpl(client, ctx, outputTable);
    }

    @Override
    public FlamePairRDD mapToPair(StringToPair lambda) throws Exception {
        String outputTable = UUID.randomUUID().toString();
        this.ctx.invokeOperation("/rdd/mapToPair", Serializer.objectToByteArray(lambda), tableName, outputTable, null);
        System.out.println("MapToPair input table: " + tableName + "; output table: " + outputTable);
        return new FlamePairRDDImpl(client, ctx, outputTable);
    }

    @Override
    public FlameRDD intersection(FlameRDD r) throws Exception {
        Iterator<Row> t1 = client.scan(this.tableName);
        Iterator<Row> t2 = client.scan(((FlameRDDImpl)r).tableName);
        Set<Row> same = getIntersection(t1, t2);
        String intersectionTable = UUID.randomUUID().toString();
        FlameRDDImpl fdi = new FlameRDDImpl(client, ctx, intersectionTable);
        for (Row row: same) {
            client.putRow(intersectionTable, row);
        }

        return fdi;
    }

    protected Set<Row> getIntersection(Iterator<Row> t1, Iterator<Row> t2) {
        if (t1 == null || t2 == null) {
            return null;
        }

        Map<String, Row> s1 = new HashMap<>();
        while (t1.hasNext()) {
            Row row = t1.next();
            s1.put(hashColumns(row), row);
        }
        Set<Row> same = new HashSet<>();
        while (t2.hasNext()) {
            Row row = t2.next();
            if (s1.containsKey(hashColumns(row))) {
                same.add(row);
            }
        }

        return same;
    }

    @Override
    public FlameRDD sample(double f) throws Exception {
        return null;
    }

    @Override
    public FlamePairRDD groupBy(StringToString lambda) throws Exception {
        Iterator<Row> iter = client.scan(tableName);
        String newTable = UUID.randomUUID().toString();
        FlamePairRDDImpl fpri = new FlamePairRDDImpl(client, ctx, newTable);
        Map<String, List<String>> cache = new HashMap<>();
        while (iter.hasNext()) {
            Row r = iter.next();
            for (String c: r.columns()) {
                String key = lambda.op(r.get(c));
                cache.computeIfAbsent(key, k -> new ArrayList<>()).add(r.get(c));
            }
        }
        for (Map.Entry<String, List<String>> entry: cache.entrySet()) {
            Row row = new Row(entry.getKey());
            row.put("values", String.join(",", entry.getValue()));
            client.putRow(newTable, row);
        }
        return fpri;
    }

    private String hashColumns(Row row) {
        StringBuilder s = new StringBuilder();
        for (String c: row.columns()) {
            s.append(row.get(c)).append(" ");
        }
        return Hasher.hash(s.toString());
    }
}
