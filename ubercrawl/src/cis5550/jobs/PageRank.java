package cis5550.jobs;

import cis5550.flame.FlameContext;
import cis5550.flame.FlamePair;
import cis5550.flame.FlamePairRDD;
import cis5550.kvs.KVSClient;
import cis5550.tools.Hasher;
import cis5550.tools.Helpers;

import java.util.ArrayList;
import java.util.Arrays;

/**
 *  Now, on to the PageRank algorithm. Create the PageRank class,
 *  and load the data into a PairRDD, just like the indexer did –
 *  except that this time, don’t make (u, p) pairs,
 *  make (u,"1.0,1.0,L") pairs, where L is a comma-separated list
 *  of normalized links you found on the page with URL hash u.
 */

public class PageRank {

    private static final double DECAY_FACTOR = 0.85;


    public static void run(FlameContext context, String[] args) throws Exception {
        // Initialize the default convergence threshold, and iterations count
        double convergenceThreshold = 0.01;
        int convergenceIterations = 0;

        // If there is at least one argument, see if we can use it to override the threshold
        if (args.length > 0) convergenceThreshold = Double.parseDouble(args[0]);

        /**
         * Now, on to the PageRank algorithm. Create the PageRank class,
         * and load the data into a PairRDD, just like the indexer did
         * except that this time, don’t make (u, p) pairs, make
         * (u,"1.0,1.0,L") pairs, where L is a comma-separated list
         * of normalized links you found on the page with URL hash u.
         * We’ll call this the “state table” below.
         */
        FlamePairRDD stateTable = context.fromTable(
            "pt-crawl", row -> row.get("url") +","+row.get("page")
        ).mapToPair(rowEntry -> {
            String[] rowParts = rowEntry.split(",");
            String url = Helpers.normalizeUrl(rowParts[0]);
            String urlHash = Hasher.hash(url);
            String page = rowParts[1];
            // Get a list of hashed urls
            String[] extractedLinksHashed = Arrays.asList(
                Helpers.extractLinks(page, url)
            ).stream().map(
                str -> Hasher.hash(str)
            ).toArray(String[]::new);

            String urlList = String.join(",", extractedLinksHashed);
            String entry = "1.0,1.0," + urlList;
            return new FlamePair(urlHash, entry);
        });

        /**
         * So far, we have only computed a single iteration, but we need
         * to iterate until convergence. Put the code so far (after the
         * initial load) into an infinite loop; at the end of the loop,
         * replace the old state table with the new one, and then compute
         * the maximum change in ranks across all the pages.
         */

        while (true) {
            /**
             * Compute the transfer table. Next, implement a single iteration
             * of the algorithm. The first step is to compute a “transfer table”
             * that specifies how much rank should flow along each link.
             *
             * For each pair (u,"rc,rp,L"), where L contains n URL hashes,
             * compute n pairs (li,v), where v = 0.85 · rc/n. In other words,
             * each of the pages that page u has a link to gets a fraction 1/n
             * of u’s current rank rc, with the decay factor d = 0.85 already
             * applied. You can use a flatMapToPair for this.
             */
            FlamePairRDD transferState = stateTable.flatMapToPair(flamePair -> {
                ArrayList<FlamePair> output = new ArrayList<>();

                String url = flamePair._1().trim();
                String[] pairValues = Arrays.asList(
                    flamePair._2().split(",")
                ).stream().map(
                    String::trim
                ).toArray(String[]::new);

                double currentRank = Double.parseDouble(pairValues[0]);
                ArrayList<String> L = Helpers.sliceStringArray(pairValues, 2, 0);

                L.forEach(urlItem -> {
                    double v = DECAY_FACTOR * currentRank / L.size();
                    output.add(new FlamePair(urlItem, Double.toString(v)));
                });

                /**
                 * You may want to additionally send rank 0.0 from each vertex to itself,
                 * to prevent vertexes with indegree zero from disappearing during the
                 * join later on.
                 */
                boolean containsSelf = L.stream().anyMatch(u -> u.equals(url));
                if (!containsSelf) output.add(new FlamePair(url, "0.0"));

                // Return the output pairs
                return output;
            });

            /**
             * Next, we need to compute the new rank each page should get,
             * by adding up all the vi from the many (u,vi) pairs for each page u.
             * You can use a foldByKey to do this; the result should be a
             * single pair (u,Pi vi) for each page.
             */
            FlamePairRDD aggregateState = transferState.foldByKey(
                "0.0",
                (a, b) -> Double.toString(
                    Double.parseDouble(a) + Double.parseDouble(b)
                )
            );

            /**
             *  First invoke join, which will combine the elements
             *  from both tables by URL hash, and concatenate the values,
             *  separated by a comma.
             */
            stateTable = stateTable.join(aggregateState)
            /**
             *  so you’ll have to follow up with another flatMapToPair that
             *  throws out the old “previous rank” entry, moves the old
             *  “current rank” entry to the new “previous rank” entry,
             *  and moves the newly computed aggregate to the “current rank”
             *  entry.
             */
            .flatMapToPair(flamePair -> {
                ArrayList<FlamePair> output = new ArrayList<>();
                String urlHash = flamePair._1();
                String[] pairValues = flamePair._2().split(",");
                double oldRank = Double.parseDouble(pairValues[0]);
                double newRank = Double.parseDouble(pairValues[pairValues.length - 1]) + (1 - DECAY_FACTOR);

                ArrayList<String> L = Helpers.sliceStringArray(
                    pairValues,
                    2,
                    pairValues.length - 1
                );

                FlamePair newPair = new FlamePair(
                    urlHash,
                    (
                        newRank + "," +
                        oldRank + "," +
                        String.join(",", L.toArray(new String[0]))
                    )
                );

                output.add(newPair);
                return output;
            });

            /**
             * Compute the maximum change in ranks across all the pages.
             * This can be done with a flatMap, where you compute the
             * absolute difference between the old and current ranks for
             * each page, followed by a fold that computes the maximum of
             * these. If the maximum is below the convergence threshold,
             * exit the loop.
             */
            double maximumChange = Double.parseDouble(
                stateTable.flatMap(flamePair -> {
                    ArrayList<String> output = new ArrayList<>();
                    String[] pairValues = flamePair._2().split(",");
                    double rc = Double.parseDouble(pairValues[0]);
                    double rp = Double.parseDouble(pairValues[1]);
                    double d = Math.abs(rc - rp); // Either positive or negative change
                    output.add(Double.toString(d));
                    return output;
                }
            )
            .fold(
                "0.0",
                (v, vi) -> Double.toString(
                    Math.max(
                        Double.parseDouble(v),
                        Double.parseDouble(vi)
                    )
                )
            ));

            // Update the convergence iteration
            convergenceIterations++;
            // Check if we need to break
            if (maximumChange < convergenceThreshold) break;
        }

        System.out.println("convergenceIterations: " + convergenceIterations);

        /**
         * Run another flatMapToPair over the state table; in the lambda,
         * decode each row, then put the rank into the pt-pageranks table
         * in the KVS, using the URL as the key and rank as the column name.
         */
        stateTable.flatMapToPair(flamePair -> {
            String url = flamePair._1();
            String rank = flamePair._2().split(",")[0];
            KVSClient kvsClient = context.getKVS();

            kvsClient.put("pt-pageranks", url, "rank", rank);
            return new ArrayList<>();
        });
    }
}
