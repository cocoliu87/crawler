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
            Map<String, List<String>> wordPosMap = posWord(page);
            for (String word: words) {
                String posUrl = url + ":" + String.join(" ", wordPosMap.get(word));
                System.out.println(word + "\n" + posUrl);
                pairs.add(new FlamePair(word, posUrl));
                pairs.add(new FlamePair(stemWord(word), posUrl));
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

    public static Map<String, List<String>> posWord(String page) {
        Map<String, List<String>> map = new HashMap<>();
        String[] strs = page.split("\\W+");
        for (int i = 0; i < strs.length; i++) {
            String word = strs[i];
            if (map.containsKey(word)) continue;
            map.computeIfAbsent(word, k -> new ArrayList<>()).add(String.valueOf(i+1));
            for (int j = i+1; j < strs.length; j++) {
                if (strs[j].equals(word)) map.get(word).add(String.valueOf(j+1));
            }
        }
        return map;
    }

    public static String stemWord(String word) {
        Stemmer s = new Stemmer();
        for (char ch: word.toCharArray()) {
            if (Character.isLetter(ch)) {
                s.add(ch);
            }
        }
        s.stem();
        return s.toString();
    }
}
