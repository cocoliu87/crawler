package cis5550.jobs;

import cis5550.flame.FlamePair;
import cis5550.flame.FlamePairRDD;
import cis5550.flame.FlameRDD;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Hasher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PageRank {
    static final String tableName = "pt-crawl", rankTableName = "pt-pageranks", delimiter = ",";
    static final double decayFactor = 0.85;
    public static void run(cis5550.flame.FlameContext ctx, String[] args) throws Exception {
        double convergence = 0.1;
        if (args.length > 0) {
            try {
                convergence = Double.parseDouble(args[0]);
            } catch (Exception ignored) {
            }
        }
        FlameRDD rdd = ctx.fromTable(tableName, row -> row.get("url") + "@" + row.get("page"));
        FlamePairRDD state = rdd.mapToPair(s -> {
            int idx = s.indexOf('@');
            String url = s.substring(0, idx);
            String page = s.substring(idx + 1);
            List<String> links = Crawler.processUrls(Crawler.getAllUrls(page), url);
            List<String> hashedLinks = new ArrayList<>();
            for (String link: links) hashedLinks.add(Hasher.hash(link));
            FlamePair p = new FlamePair(Hasher.hash(url), "1.0,1.0," + String.join(delimiter, hashedLinks));
//            System.out.println(p);
            return p;
        });
        while (true) {
            // Compute and aggregate transfer table
            FlamePairRDD transfer = state.flatMapToPair(p -> {
                List<FlamePair> pairs = new ArrayList<>();
                String[] values = p._2().split(delimiter);
                double cRank = Double.parseDouble(values[0].strip());
                double n =values.length - 2;
                for (int i = 2; i < values.length; i++) {
                    pairs.add(new FlamePair(values[i], String.valueOf(cRank * decayFactor / n)));
                }
                // You may want to additionally send rank 0.0 from each vertex to itself,
                // to prevent vertexes with indegree zero from disappearing during the join later on.
                pairs.add(new FlamePair(p._1(), "0.0"));
//                System.out.println(Arrays.toString(pairs.toArray()));
                return pairs;
            }).foldByKey("0", (a, b) -> "" + (Double.parseDouble(a) + Double.parseDouble(b)));

            // Join state and transfer tables
            state = state.join(transfer).flatMapToPair(p -> {
                List<FlamePair> pairs = new ArrayList<>();
                String[] values = p._2().split(delimiter);
                int len = values.length;
                // This is also a good opportunity to add the 0.15 from the rank source.
                values[1] = values[0];
                values[0] = String.valueOf(Double.parseDouble(values[len - 1]) + 0.15);
                values = Arrays.copyOf(values, values.length-1);
                pairs.add(new FlamePair(p._1(), String.join(delimiter, values)));
                return pairs;
            });

            // calculate the convergence
            String max = state.flatMap(p -> {
                String[] values = p._2().split(delimiter);
                double diff = Math.abs(Double.parseDouble(values[0]) - Double.parseDouble(values[1]));
                return List.of(new String[]{String.valueOf(diff)});
            }).fold("0", (s1, s2) -> Double.parseDouble(s1) >= Double.parseDouble(s2)? s1 : s2);

            // if meet the requirement, quit the loop
            if (Double.parseDouble(max) < convergence) break;
        }


        state.flatMapToPair(p -> {
            KVSClient client = ctx.getKVS();
            Row r = new Row(p._1());
            r.put("rank", p._2().strip().split(delimiter)[0]);
            client.putRow(rankTableName, r);
            return List.of();
        });
    }
}
