package cis5550.flame;

import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Serializer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static cis5550.flame.Worker.Delimiter;

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

            while (iter.hasNext()) {
                Row r = iter.next();
                for (String c: r.columns()) {
                    pairs.add(new FlamePair(r.key().split(Delimiter)[0], new String(r.getBytes(c), StandardCharsets.UTF_8)));
                }
            }
        }
        return pairs;
    }

    @Override
    public FlamePairRDD foldByKey(String zeroElement, TwoStringsToString lambda) throws Exception {
        String outputTable = UUID.randomUUID().toString();
        this.ctx.invokeOperation("/rdd/foldByKey", Serializer.objectToByteArray(lambda), tableName, outputTable, zeroElement);
        System.out.println("FoldByKey input table: " + tableName + "; output table: " + outputTable);
        return new FlamePairRDDImpl(client, ctx, outputTable);
    }
}
