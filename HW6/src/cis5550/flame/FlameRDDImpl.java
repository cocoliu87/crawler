package cis5550.flame;

import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Serializer;

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
        return new FlamePairRDDImpl(client, ctx, outputTable);
    }

    @Override
    public FlameRDD intersection(FlameRDD r) throws Exception {
        return null;
    }

    @Override
    public FlameRDD sample(double f) throws Exception {
        return null;
    }

    @Override
    public FlamePairRDD groupBy(StringToString lambda) throws Exception {
        return null;
    }
}
