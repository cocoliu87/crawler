/**
 * UberCrawl Search API.
 *
 * The purpose of this program is to act as an API endpoint
 * utilizing the web server functions.
 *
 * 1. It breaks down the query into single terms (or tokens), example:
 * "Philadelphia Eagles Score" into {"philadelphia", "eagles", "score"}.
 *
 * 2. For every token, it gathers the urls from the pt-index table.
 *
 * 3. For every url, it hashes the url and gathers page rank, and TF/IDF values.
 *
 * 4. Builds a TF-DF table of possibly relevant results, then performs
 * a cosine-similarity calculation on all of those items in the table.
 * The score is based on the SUM of (page rank) and (cosine score).
 * S=(PR+CS)
 *
 * 5. It sorts the results by descending score value (highest first).
 *
 * 6. Builds a JSON-formatted result and renders that as the
 * content to the front-end.
 *
 * @author Sergio Garcia <gsergio@seas.upenn.edu>
 */
package cis5550.api;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import static cis5550.webserver.Server.*;

import cis5550.kvs.KVSClient;
import cis5550.tools.*;


class SearchAPI {

    private static final Logger logger = Logger.getLogger(SearchAPI.class);

    private static final HashSet<String> stopWords = new HashSet<>(List.of("a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "arent", "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by", "cant", "cannot", "could", "couldnt", "did", "didnt", "do", "does", "doesnt", "doing", "dont", "down", "during", "each", "few", "for", "from", "further", "had", "hadnt", "has", "hasnt", "have", "havent", "having", "he", "hed", "hell", "hes", "her", "here", "heres", "hers", "herself", "him", "himself", "his", "how", "hows", "i", "id", "ill", "im", "ive", "if", "in", "into", "is", "isnt", "it", "its", "its", "itself", "lets", "me", "more", "most", "mustnt", "my", "myself", "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves", "out", "over", "own", "same", "shant", "she", "shed", "shell", "shes", "should", "shouldnt", "so", "some", "such", "than", "that", "thats", "the", "their", "theirs", "them", "themselves", "then", "there", "theres", "these", "they", "theyd", "theyll", "theyre", "theyve", "this", "those", "through", "to", "too", "under", "until", "up", "very", "was", "wasnt", "we", "wed", "well", "were", "weve", "were", "werent", "what", "whats", "when", "whens", "where", "wheres", "which", "while", "who", "whos", "whom", "why", "whys", "with", "wont", "would", "wouldnt", "you", "youd", "youll", "youre", "youve", "your", "yours", "yourself", "yourselves"));

    private static HashMap<String, Integer> resultsCount = new HashMap<>();

    private static final int RESULTS_PER_PAGE = 20;

    private static String mockSearch(String mockDataFile) throws IOException {
        FileInputStream fis = new FileInputStream(mockDataFile);
        byte[] buffer = new byte[10];
        StringBuilder sb = new StringBuilder();
        while (fis.read(buffer) != -1) {
            sb.append(new String(buffer));
            buffer = new byte[10];
        }
        fis.close();
        return sb.toString();
    }


    /**
     * Calculates tfIdf values for each search term
     * @param query - A string containing the search term
     * @return
     */
    public static HashMap<String, Double> vectorize(String query, HashMap<String, Double> idfValues) {
        // Stores the word->values in a map
        HashMap<String, Double> tfidfVector = new HashMap<>();
        // Split query into words
        String[] words = Helpers.getWords(query);

        // Compute term frequencies for the query
        HashMap<String, Integer> tf = new HashMap<>();
        for (String word : words) {
            String hashedWord = Hasher.hash(word);
            int currentSearchTfValue = tf.getOrDefault(hashedWord, 0);
            tf.put(hashedWord, currentSearchTfValue + 1);
        }

        // Compute TF-IDF values for each term
        for (String term : tf.keySet()) {
            double tfidf = (1 + Math.log(tf.get(term))) * idfValues.getOrDefault(term, 0.0);
            tfidfVector.put(term, tfidf);
        }

        // System.out.println("vectorize("+query+") idfValues: " + idfValues + ", tfidfVector: " + tfidfVector);

        return tfidfVector;
    }

    private static double computeCosineSimilarity(Map<String, Double> vector1, Map<String, Integer> vector2) {
        // The sum of the element-wise multiplication of corresponding components of the vectors.
        double dotProduct = 0.0;

        // The Euclidean norms (magnitude) of the vectors, calculated by summing the squares of their components.
        double norm1 = 0.0;
        double norm2 = 0.0;

        /**
         * The loop iterates over the terms in vector1. For each term,
         * it calculates the product of the corresponding TF-IDF values
         * in vector1 and the term frequency in vector2, adding this to
         * dotProduct. It also adds the square of the TF-IDF value to norm1.
         */
        for (String term : vector1.keySet()) {
            double tfIdf = vector1.get(term);
            double tf = vector2.getOrDefault(term, 0);
            dotProduct += tfIdf * tf;
            norm1 += tfIdf * tfIdf;
        }

        /**
         * Iterate over the term frequencies in vector2, adding
         * the square of each term frequency to norm2.
         */
        for (int tf : vector2.values()) {
            norm2 += tf * tf;
        }

        /**
         * Finally, the method returns the cosine similarity,
         * which is the dot product divided by the product of
         * the Euclidean norms. If either of the norms is zero
         * (indicating a zero vector), it returns 0.0 to avoid
         * division by zero.
         */
        // Avoid division by zero
        if (norm1 == 0 || norm2 == 0) return 0.0;
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Performs a search against KVS
     * @param kvsClient - The KVS client object (to connect to kvs)
     * @param query - A string containing the query to search
     * @param fromIndex - Results start index, an integer to help with pagination
     * @param toIndex - Results end index, an interger to help with pagination.
     * @return
     * @throws IOException
     */
    public static List<SearchResult> search(KVSClient kvsClient, String query, int fromIndex, int toIndex) throws IOException {
        System.out.println("search() Performing search for: " + query);
        ArrayList<SearchResult> results = new ArrayList<>();
        HashMap<String, SearchDocument> documents = new HashMap<>();
        HashMap<String, String> urlHashes = new HashMap<>();
        HashMap<String, Double> idfValues = new HashMap<>();

        /**
         * I. Retrieve List of documents based on search terms.
         * For every term:
         * - Retrieve the urls from the index table
         * - Get the IDF value from the idf table
         */

        // 1. The query needs to be broken down to stemmed terms
        String[] stemmedQueryTerms = Helpers.getWords(query);

        // 2. For each word, retrieve the urls from the index, and
        for(String stemmedQueryTerm : stemmedQueryTerms) {

            // Hashed term
            String hashedTerm = Hasher.hash(stemmedQueryTerm);

            // Get the urls for every term
            String urlsString = Helpers.getKvsDefault(
                kvsClient,
                "pt-index",
                "" + hashedTerm,
                "acc",
                ""
            );

            double termIdfValue = Double.parseDouble(Helpers.getKvsDefault(
                kvsClient,
                "pt-term-idf",
                "" + hashedTerm,
                "value",
                "0.0"
            ));

            System.out.println("search() Performing index pull for stemmedQueryTerm: " + stemmedQueryTerm + ", hashed: " + hashedTerm);
            System.out.println("search() Index's urlsString: " + urlsString + ", for hashed: " + hashedTerm);
            System.out.println("search() Term IDF Value: " + termIdfValue);

            idfValues.put(hashedTerm, termIdfValue);

            // Next, if the urls aren't empty, then try to get a list of them
            if(!urlsString.isEmpty()) {
                String[] indexUrls = urlsString.split(",");
                resultsCount.put(Hasher.hash(query), indexUrls.length);

                List<String> indexSubset = new ArrayList<>(Arrays.asList(indexUrls));

                if(indexUrls.length >= RESULTS_PER_PAGE) {
                    indexSubset = indexSubset.subList(fromIndex, toIndex);
                }

                // Split it, and for every url, add the hash to our accumulator
                for(String urlItem : indexSubset) {
                    String decodedUrlItem = Helpers.decode64(urlItem);
                    String urlHash = Hasher.hash(decodedUrlItem);
                    urlHashes.put(urlHash, decodedUrlItem);
                }
            }
        }

        /**
         * 3. For every url hash found in the accumulator
         * The main objective of this loop is to prepare the
         * search corpus for the cosine similarity calculation.
         */
        for(String urlHash : urlHashes.keySet()) {

            // Gather each map from 'pt-term-freq' table, and deserialize
            String pageRankTermFreqStr = Helpers.getKvsDefault(
                kvsClient,
                "pt-term-freq",
                urlHash,
                "term_freq",
                ""
            );

            pageRankTermFreqStr = "0.0@" + pageRankTermFreqStr + "@Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";


            // Parse the page rank and serializer
            if(pageRankTermFreqStr.contains("@")) {
                // First we split the string into its three parts
                String[] documentTuple = pageRankTermFreqStr.split("@");

                // First part is page rank, the term freq map, and text
                String pageRankStrPart = documentTuple[0];
                String tfStrPart = documentTuple[1];
                String text = documentTuple[2];

                SearchDocument parsedDoc = new SearchDocument(
                    "" + pageRankStrPart,
                    "" + urlHashes.get(urlHash),
                    "" + text,
                    "" + tfStrPart
                );

                documents.put(urlHash, parsedDoc);
            }
        }


        /**
         * II. For each document, calculate cosine similarity
         */

        /**
         * For every document we have collected
         */
        for (String urlHash : documents.keySet()) {
            // Get the document in question
            SearchDocument currentDocument = documents.get(urlHash);

            // Vectorize search terms
            HashMap<String, Double> queryVector = vectorize(query, idfValues);
            HashMap<String, Integer> docTf = currentDocument.getTermFreq();

            double cosineSimilarity = computeCosineSimilarity(queryVector, docTf);

            SearchResult sr = new SearchResult(
                currentDocument,
                cosineSimilarity
            );

            results.add(sr);
        }

        // Lastly, sort by score (highest first, descending order)
        results.sort((r1, r2) -> Double.compare(r2.getScore(), r1.getScore()));

        System.out.println("Results: " + results);
        return results;
    }

    public static String[] attributesToJson(HashMap<String, String> attributes) {
        List<String> attrs = new ArrayList<>();

        for(String k : attributes.keySet()) {
            attrs.add("\"k\":\"v\"");
        }

        return attrs.toArray(String[]::new);
    }

    /**
     * Given a result list, it turns the list into a readable/parsable json document
     * @param attributes
     * @param results
     * @return
     */
    public static String generateJsonResponse(HashMap<String, String> attributes, List<SearchResult> results) {
        return """
            {
                %ATTRIBUTES%
                "total": %TOTAL_RESULTS%,
                "results": [%RESULTS%]
            }    
        """
        // Insert any attributes if they are present
        .replaceFirst("%ATTRIBUTES%",
            String.join(",",
                attributesToJson(attributes)
            )
        )
        // Then we patch in the total results
        .replaceFirst("%TOTAL_RESULTS%", Integer.toString(resultsCount.get(Hasher.hash(query))))
        // Then we provide an object array with the actual results
        .replaceFirst("%RESULTS%",
            String.join(",",
                // This basically gathers every result as a json string, joins with a comma
                results.stream().map(item -> item.toJson()).toArray(String[]::new)
            )
        );
    }

    /**
     * Prepares arguments for actual search
     * @param query
     * @param page
     * @return
     * @throws IOException
     */
    private static String performSearch(String query, String page) throws IOException {
        // Establish connection to host
        KVSClient kvsClient = new KVSClient("localhost:8000");

        // Calculate the current array indexes (for pagination)
        int pageNum = 0, fromIndex = 0, toIndex = 0, resultsPerPage = RESULTS_PER_PAGE;

        if(page != null && !page.isEmpty()) {
            try {
                pageNum = Math.max(Math.abs(Integer.parseInt(page)), 1) - 1;
            } catch (Exception e) {
                pageNum = 0;
            }
        }

        fromIndex = pageNum * resultsPerPage;
        toIndex = fromIndex + (resultsPerPage - 1);

        HashMap<String, String> attributes = new HashMap<>();
        attributes.put("query", query.replaceAll("\"", ""));
        attributes.put("page", page);
        attributes.put("fromIndex", "" + fromIndex);
        attributes.put("toIndex", "" + toIndex);

        // Perform search
        List<SearchResult> results = search(
            kvsClient,
            query,
            fromIndex,
            toIndex
        );

        // Display results
        return generateJsonResponse(attributes, results);
    }


    public static void main(String args[]) {
        // Initialize port
        port(Integer.parseInt(args[0]));

        staticFiles.location("static");

        post("/api/search", (req,res) -> {
            // Gather the query and page number (default 1)
            String query = req.queryParams("query");
            String page = req.queryParams("page");

            // Display results
            res.type("application/json");
            return performSearch(query, page);
        });

        get("/api/search", (req,res) -> {
            // Gather the query and page number (default 1)
            String query = req.queryParams("query");
            String page = req.queryParams("page");

            // Display results
            res.type("application/json");
            return performSearch(query, page);
        });
    }
}