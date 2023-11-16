package cis5550.jobs;

import cis5550.flame.*;
import java.util.Arrays;
import java.util.HashSet;

public class Indexer {

    /**
     * As a first step, create the class for the indexer, and, in its run method.
     * @param context
     * @param args
     * @throws Exception
     */
    public static void run(FlameContext context, String[] args) throws Exception {

        /**
         * Load the data from the pt-crawl table. Ideally, we would like a PairRDD of (u, p) pairs,
         * where u is a normalized URL and p is the contents of the corresponding page,
         * but we only have a fromTable for normal RDDs.
         */


        /**
         * First, use fromTable and map each Row of the pt-crawl table to a string u,p,
         * where u is the URL and p the page, and then use mapToPair to convert this
         * into a PairRDD again.
         */

        FlamePairRDD flamePairRDD = context.fromTable(
                "pt-crawl",
                // we would like a PairRDD of (u, p) pairs,
                row -> {
                    return
                        row.get("url") +  // where u is the URL
                        "," +
                        row.get("page");  // and p the page
                }
        )
        // And then use mapToPair
        .mapToPair(pairString -> {
            int dividerIndex = pairString.indexOf(",");
            String url = pairString.substring(0, dividerIndex);
            String page = pairString.substring(dividerIndex + 1);
            // Convert this into a PairRDD again.
            FlamePair flamePair = new FlamePair(url, page);
            return flamePair;
        });


        /**
         * Next, create the inverted index. This involves two simple steps.
         * First, we need to convert each (u, p) pair to lots of (w, u) pairs,
         * where w is a word that occurs in p. You can use flatMapToPair for this.
         */
        flamePairRDD.flatMapToPair(kvPair -> {
            // First, we create a set of FlamePairs, which is our output
            // A hashmap would be faster, but
            HashSet<FlamePair> wordUrlPairs = new HashSet<>();
            // Gather the url, and the page
            String url = kvPair._1().trim(); // Key: url
            String page = kvPair._2().trim(); // Value: page contents

            /**
             * Make sure to filter out all HTML tags, to remove all punctuation,
             * and to convert everything to lower case.
             */
            // Filter out all html tags
            page = page.replaceAll("<[^>]*>", "");
            // Remove all punctuation
            page = page.replaceAll("\\p{Punct}", ""); // !”#$%&'()*+,-./:;<=>?@[\]^_`{|}~:
            // convert everything to lower case
            page = page.toLowerCase();

            /**
             * The resulting PairRDD will still contain lots of (w, ui) pairs,
             * with the same word w but different URLs ui.
             */
            // Break the page contents into words
            Arrays.stream(page.split("\\s+")).forEach(word -> {
                // For each word, crate a unique pair. Uniqueness guaranteed by HashSet.
                wordUrlPairs.add(
                    new FlamePair(word, url)
                );
            });
            // Output the unique set for folding
            return wordUrlPairs;
        })
        /**
         * We’ll need to fold all the URLs into a single
         * comma-separated list, using foldByKey.
         */
        .foldByKey(
            "",
            (a, b) ->
                // Lastly we just have to join with a comma
                a.isEmpty()
                    ? a + b // If empty, we just put them together
                    : a + "," + b // If not, then separate
        )
        /**
         * This should produce the required data; you can rename the final PairRDD
         * to pt-index using the saveAsTable method.
         */
        .saveAsTable("pt-index");
    }
}
