package cis5550.jobs;

import cis5550.flame.*;
import cis5550.tools.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class Indexer {
    static final String tableName = "pt-crawl", indexerTableName = "pt-index";

    public static void run(cis5550.flame.FlameContext ctx, String[] args) throws Exception {
        Set<String> dict = loadWordsToDicts("dict20k.txt");
        Set<String> stops = loadWordsToDicts("dictStopWords.txt");
        
        System.out.println("processing page from pt-crawl table");
        
        FlameRDD rdd = ctx.fromTable(tableName, row -> row.get("url") + "@" + row.get("page"));
        System.out.println("processing mapToPair");
        FlamePairRDD pRdd = rdd.mapToPair(s -> {
            int idx = s.indexOf('@');
            return new FlamePair(s.substring(0, idx), s.substring(idx+1));
        }).flatMapToPair(p -> {
            String page = p._2();
            String url = p._1();

            System.out.println("processing url: "+url);
            page = removeAllSpecialCharacters(page).toLowerCase();
            Set<String> words = new HashSet<>();
            System.out.println("Dict size is "+dict.size()+"; Stop Words size is "+stops.size());

            for (String w: page.split("\\W+")) {
                if (dict.contains(w) && !stops.contains(w)) {
                    words.add(w);
                } else {
                    System.out.println("not adding word -- "+w);
                }
            }
            
            System.out.println("got words size: "+words.size());
            List<FlamePair> pairs = new ArrayList<>();
            Map<String, List<String>> wordPosMap = posWord(page);
            for (String word: words) {
                System.out.println("processing word: "+word);
                String posUrl = Helpers.encode64(url) + ":" + String.join(" ", wordPosMap.get(word));
                System.out.println(word + "\n" + posUrl);
                pairs.add(new FlamePair(Hasher.hash(word), posUrl));
                pairs.add(new FlamePair(Hasher.hash(Helpers.stemWord(word)), posUrl));
            }
            return pairs;
        }).foldByKey("", (s1, s2) -> s1 + (s1.isEmpty()? "":",") + s2);
        pRdd.saveAsTable(indexerTableName);
    }

    public static String removeAllSpecialCharacters(String page) {
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

    private static Set<String> loadWordsToDicts(String fileName) throws IOException {
        Set<String> dict = new HashSet<>();
        try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
            stream.forEach(s -> {
                if (!s.startsWith("#")) {
                    // System.out.println("word dict is loading -- "+s);
                    dict.add(s.toLowerCase());
                }
            });
        }
        return dict;

        // try (Stream<String> stream = Files.lines(Paths.get("dictStopWords.txt"))) {
        //     stream.forEach(s -> {
        //         if (!s.startsWith("#")) {
        //             // System.out.println("stop words set is loading -- "+s);
        //             stops.add(s.toLowerCase());
        //         }
        //     });
        // }
    }
}