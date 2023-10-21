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
    public int count() throws Exception {
        return client.count(tableName);
    }

    @Override
    public void saveAsTable(String tableNameArg) throws Exception {
        client.rename(tableName, tableNameArg);
    }

    @Override
    public FlameRDD distinct() throws Exception {
        String outputTable = UUID.randomUUID().toString();
        Iterator<Row> rows = client.scan(tableName);
        while (rows.hasNext()) {
            Row oriRow = rows.next();
            for (String c: oriRow.columns()) {
                String v = oriRow.get(c);
                Row newRow = new Row(Hasher.hash(v));
                newRow.put("value", v);
                client.putRow(outputTable, newRow);
            }
        }
        return new FlameRDDImpl(client, ctx, outputTable);
    }

    @Override
    public void destroy() throws Exception {

    }

    @Override
    public Vector<String> take(int num) throws Exception {
        Iterator<Row> rows = client.scan(tableName);
        Vector<String> output = new Vector<>();
        while (rows.hasNext() && num > 0) {
            Row row = rows.next();
            num--;
            for (String c: row.columns()) {
                output.add(row.get(c));
            }
        }
        return output;
    }

    @Override
    public String fold(String zeroElement, FlamePairRDD.TwoStringsToString lambda) throws Exception {
        return null;
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
        this.ctx.invokeOperation("/rdd/flatMap", Serializer.objectToByteArray(lambda), tableName, outputTable, null, null);
        return new FlameRDDImpl(client, ctx, outputTable);
    }

    @Override
    public FlamePairRDD flatMapToPair(StringToPairIterable lambda) throws Exception {
        String outputTable = UUID.randomUUID().toString();
        this.ctx.invokeOperation("/rdd/flatMapToPair", Serializer.objectToByteArray(lambda), tableName, outputTable, null, null);
        return new FlamePairRDDImpl(client, ctx, outputTable);
    }

    @Override
    public FlamePairRDD mapToPair(StringToPair lambda) throws Exception {
        String outputTable = UUID.randomUUID().toString();
        this.ctx.invokeOperation("/rdd/mapToPair", Serializer.objectToByteArray(lambda), tableName, outputTable, null, null);
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
        Iterator<Row> iter = client.scan(tableName);
        String newTable = UUID.randomUUID().toString();
        FlameRDDImpl rdd = new FlameRDDImpl(client, ctx, newTable);
        for (Row r: sampleRows(iter, f)) {
            client.putRow(newTable, r);
        }
        return rdd;
    }

    protected List<Row> sampleRows(Iterator<Row> iter, double f) {
        List<Row> rows = new ArrayList<>();
        iter.forEachRemaining(rows::add);
        int sampleSize = (int)(rows.size() * f);
        Row[] samples = new Row[sampleSize];
        int idx;
        for (idx = 0; idx < sampleSize; idx++) {
            samples[idx] = rows.get(idx);
        }

        Random rand = new Random();

        for (; idx < rows.size(); idx++) {
            int s = rand.nextInt(idx + 1);
            if (s < sampleSize) {
                samples[s] = rows.get(idx);
            }
        }
        return Arrays.asList(samples);
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

    @Override
    public FlameRDD filter(StringToBoolean lambda) throws Exception {
        return null;
    }

    @Override
    public FlameRDD mapPartitions(IteratorToIterator lambda) throws Exception {
        return null;
    }

    private String hashColumns(Row row) {
        StringBuilder s = new StringBuilder();
        for (String c: row.columns()) {
            s.append(row.get(c)).append(" ");
        }
        return Hasher.hash(s.toString());
    }
}
