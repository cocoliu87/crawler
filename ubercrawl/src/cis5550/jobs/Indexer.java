package cis5550.jobs;

import cis5550.flame.FlameContext;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Hasher;
import cis5550.external.PorterStemmer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Indexer {
    public static void run(FlameContext ctx, String[] args) throws Exception {
        KVSClient kvs = ctx.getKVS();

        List<String> normalizedPages = new ArrayList<>();

        // Iterate through workers and fetch data for each row individually
        for (int i = 0; i < kvs.numWorkers(); i++) {
            String workerAddress = kvs.getWorkerAddress(i);

            // Fetch rows from pt-crawl table for the current worker
            String tableName = "pt-crawl";
            Iterator<Row> rowIterator = fetchRowsFromWorker(kvs, tableName, workerAddress);
            // Process rows and perform indexing
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                String url = row.get("url");
                String content = row.get("page");

                if (url == null || content == null) continue;

                // Process rows and perform indexing
                try {
                    processRow(ctx, row);
                } catch (Exception ex){
                    System.out.println("Handle processing row exception" + ex.getMessage());
                }

                // Normalize the URL and create (u, p) pairs as strings
                String normalizedUrl = normalizeUrl(url);
                for (String s : content.split(" ")) {
                    try {
                        kvs.put("pt-index", s, "url", url);
                    } catch (Exception ex) {
                        System.out.println("Handle putting row exception" + ex.getMessage());
                    }
                }

                normalizedPages.add(normalizedUrl + "," + content);
            }
        }

        // After saving the pt-index table, fetch and print its content
        Iterator<Row> indexTableIterator = kvs.scan("pt-index", null, null);
        System.out.println("Content of pt-index table:");
        while (indexTableIterator.hasNext()) {
            Row indexRow = indexTableIterator.next();
            String key = indexRow.key();
            String value = indexRow.columns().iterator().next(); // Assuming there's only one column per row
        }

    }

    private static Iterator<Row> fetchRowsFromWorker(KVSClient kvs, String tableName, String workerAddress) {
        try {
            return kvs.scan(tableName);
        } catch (IOException e) {
            // Handle the exception (e.g., log the error)
            e.printStackTrace();
            return Collections.emptyIterator();
        }
    }

    private static void processRow(FlameContext ctx, Row row) {
//        if (row.get("url") == null || row.get("page") == null || row.get("contentType") == null) return;
        String url = row.get("url");
        byte[] contentBytes = row.get("page").getBytes();
        String contentType = row.get("contentType");

        if (contentType != null && contentType.startsWith("text/html")) {
            String content = new String(contentBytes, StandardCharsets.UTF_8);
            String hashedUrl = Hasher.hash(url);

            String indexedContent = performIndexing(content, ctx);
            System.out.println("Indexed Content: " + indexedContent);

            // Store indexed data using KVS
            try {
                System.out.println("Hashed URL: " + hashedUrl);
                ctx.getKVS().put("pt-index", row.get("page"), "indexedContent", indexedContent);
            } catch (IOException e) {
                // Handle the exception (e.g., log the error)
                e.printStackTrace();
            }
        }
    }

    private static String performIndexing(String content, FlameContext ctx) {
        String[] words = content.split("\\s+|\\p{Punct}");
        PorterStemmer stemmer = new PorterStemmer(); // Instantiate the stemmer

        Map<String, Set<String>> wordIndex = new HashMap<>();
        StringBuilder indexedContent = new StringBuilder();

        for (String word : words) {
            // Normalize the word to lowercase and skip short words
            word = word.toLowerCase();
            if (word.length() > 2) {
                // Remove any non-alphabetic characters from the word
                word = word.replaceAll("[^a-zA-Z]", "");
                // Skip empty words after removing non-alphabetic characters
                if (!word.isEmpty()) {
                    // Stem the word using PorterStemmer
                    stemmer.add(word.toCharArray(), word.length());
                    stemmer.stem();
                    String stemmedWord = stemmer.toString();

                    // Update the index map with the original word
                    wordIndex.computeIfAbsent(word, k -> new HashSet<>()).add(word);
                    // Update the index map with the stemmed word
                    wordIndex.computeIfAbsent(stemmedWord, k -> new HashSet<>()).add(word);

                    // Now add the original and stemmed words to the KVS
                    try {
                        System.out.println("Indexing word: " + word + " (Stemmed: " + stemmedWord + ")");
                        ctx.getKVS().put("pt-index", word, "indexedContent", String.valueOf(indexedContent));
                        ctx.getKVS().put("pt-index", stemmedWord, "indexedContent", String.valueOf(indexedContent));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        // Build the indexed content as a string
        for (Map.Entry<String, Set<String>> entry : wordIndex.entrySet()) {
            String word = entry.getKey();
            Set<String> urls = entry.getValue();

            // Format: word:url1,url2,url3,...
            String indexedWord = word + ":" + String.join(",", urls);

            // Append the indexed word to the indexed content
            indexedContent.append(indexedWord).append(" ");
        }

        // Trim the trailing space and return the indexed content
        return indexedContent.toString().trim();
    }

    private static String normalizeUrl(String url) {
        // Convert the URL to lowercase
        String normalizedUrl = url.toLowerCase();

        int fragmentIndex = normalizedUrl.indexOf('#');
        if (fragmentIndex != -1) {
            normalizedUrl = normalizedUrl.substring(0, fragmentIndex);
        }

        // Remove trailing slash (except for the root URL)
        if (normalizedUrl.endsWith("/") && normalizedUrl.length() > 1) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 1);
        }

        // Remove default port numbers (e.g., http://example.com:80/)
        normalizedUrl = normalizedUrl.replaceAll(":80/", "/");

        // Remove www subdomain (optional, based on preference)
        normalizedUrl = normalizedUrl.replaceFirst("www\\.", "");

        return normalizedUrl;
    }
}