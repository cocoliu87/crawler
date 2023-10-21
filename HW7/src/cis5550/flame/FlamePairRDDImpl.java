package cis5550.flame;

import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Serializer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;


public class FlamePairRDDImpl implements FlamePairRDD {
    KVSClient client;
    FlameContextImpl ctx;
    String tableName;

    public FlamePairRDDImpl(KVSClient client, FlameContextImpl ctx, String tableName) {
        this.client = client;
        this.ctx = ctx;
        this.tableName = tableName;
    }


    @Override
    public List<FlamePair> collect() throws Exception {
        List<FlamePair> pairs = new ArrayList<>();
        if (!tableName.isEmpty() && client != null) {
            Iterator<Row> iter = client.scan(tableName);
            System.out.println("Collecting table: " + tableName);

            while (iter.hasNext()) {
                Row r = iter.next();
                for (String c: r.columns()) {
                    pairs.add(new FlamePair(r.key(), new String(r.getBytes(c), StandardCharsets.UTF_8)));
                    //pairs.add(new FlamePair(r.key().split("@")[0], new String(r.getBytes(c), StandardCharsets.UTF_8)));
                }
            }
        }
        return pairs;
    }

    @Override
    public FlamePairRDD foldByKey(String zeroElement, TwoStringsToString lambda) throws Exception {
        String outputTable = UUID.randomUUID().toString();
        this.ctx.invokeOperation("/rdd/foldByKey", Serializer.objectToByteArray(lambda), tableName, outputTable, zeroElement, null);
        System.out.println("FoldByKey input table: " + tableName + "; output table: " + outputTable);
        return new FlamePairRDDImpl(client, ctx, outputTable);
    }

    @Override
    public void saveAsTable(String tableNameArg) throws Exception {
        Iterator<Row> rows = client.scan(tableName);
        while(rows.hasNext()) {
            Row r = rows.next();
            for (String c: r.columns()) {
                client.put(tableNameArg, r.key(), c, r.get(c));
            }
        }
    }

    @Override
    public FlameRDD flatMap(PairToStringIterable lambda) throws Exception {
        String outputTable = UUID.randomUUID().toString();
        this.ctx.invokeOperation("/pairRdd/flatMap", Serializer.objectToByteArray(lambda), tableName, outputTable, null, null);
        return new FlameRDDImpl(client, ctx, outputTable);
    }

    @Override
    public void destroy() throws Exception {

    }

    @Override
    public FlamePairRDD flatMapToPair(PairToPairIterable lambda) throws Exception {
        return null;
    }

    @Override
    public FlamePairRDD join(FlamePairRDD other) throws Exception {
        String outputTable = UUID.randomUUID().toString();
        this.ctx.invokeOperation("/pairRDD/join", null, tableName, outputTable, null, ((FlamePairRDDImpl)other).tableName);
        //System.out.println("FoldByKey input table: " + tableName + "; output table: " + outputTable);
        return new FlamePairRDDImpl(client, ctx, outputTable);
    }

    @Override
    public FlamePairRDD cogroup(FlamePairRDD other) throws Exception {
        return null;
    }
}
