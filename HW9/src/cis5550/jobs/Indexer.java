package cis5550.jobs;

import cis5550.flame.*;

import java.util.*;

public class Indexer {
    static final String tableName = "pt-crawl", indexerTableName = "pt-index";
    public static void run(cis5550.flame.FlameContext ctx, String[] args) throws Exception {
        FlameRDD rdd = ctx.fromTable(tableName, row -> row.get("url") + "@" + row.get("page"));
        FlamePairRDD pRdd = rdd.mapToPair(s -> {
            int idx = s.indexOf('@');
            return new FlamePair(s.substring(0, idx), s.substring(idx+1));
        }).flatMapToPair(p -> {
            String page = p._2();
            String url = p._1();
            page = removeAllSpecialCharacters(page);
            Set<String> words = new HashSet<>(Arrays.asList(page.split("\\W+")));
            List<FlamePair> pairs = new ArrayList<>();
            for (String word: words) {
                pairs.add(new FlamePair(word, url));
            }
            return pairs;
        }).foldByKey("", (s1, s2) -> s1 + (s1.isEmpty()? "":",") + s2);
        pRdd.saveAsTable(indexerTableName);
    }

    public static String removeAllSpecialCharacters(String page) {
        // replaceAll("\<.*?\>", "")
        return page.replaceAll("<[^>]*>", "")
                .replaceAll("\\p{Punct}", "")
                .toLowerCase();
    }
}
