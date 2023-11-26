package cis5550.api;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import static cis5550.webserver.Server.*;

import cis5550.kvs.KVSClient;
import cis5550.tools.*;


class SearchAPI {

    private static final Logger logger = Logger.getLogger(SearchAPI.class);

    private final HashSet<String> stopWords = new HashSet<>(List.of("a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "arent", "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by", "cant", "cannot", "could", "couldnt", "did", "didnt", "do", "does", "doesnt", "doing", "dont", "down", "during", "each", "few", "for", "from", "further", "had", "hadnt", "has", "hasnt", "have", "havent", "having", "he", "hed", "hell", "hes", "her", "here", "heres", "hers", "herself", "him", "himself", "his", "how", "hows", "i", "id", "ill", "im", "ive", "if", "in", "into", "is", "isnt", "it", "its", "its", "itself", "lets", "me", "more", "most", "mustnt", "my", "myself", "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves", "out", "over", "own", "same", "shant", "she", "shed", "shell", "shes", "should", "shouldnt", "so", "some", "such", "than", "that", "thats", "the", "their", "theirs", "them", "themselves", "then", "there", "theres", "these", "they", "theyd", "theyll", "theyre", "theyve", "this", "those", "through", "to", "too", "under", "until", "up", "very", "was", "wasnt", "we", "wed", "well", "were", "weve", "were", "werent", "what", "whats", "when", "whens", "where", "wheres", "which", "while", "who", "whos", "whom", "why", "whys", "with", "wont", "would", "wouldnt", "you", "youd", "youll", "youre", "youve", "your", "yours", "yourself", "yourselves"));

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
            tf.put(word, tf.getOrDefault(word, 0) + 1);
        }

        // Compute TF-IDF values for each term
        for (String term : tf.keySet()) {
            double tfidf = (1 + Math.log(tf.get(term))) * idfValues.getOrDefault(term, 0.0);
            tfidfVector.put(term, tfidf);
        }

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
    public List<SearchResult> search(KVSClient kvsClient, String query, int fromIndex, int toIndex) throws IOException {
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
            // Get the urls for every term
            String urlsString = Helpers.getKvsDefault(
                kvsClient,
                "pt-index",
                "" + stemmedQueryTerm,
                "acc",
                ""
            );

            double termIdfValue = Double.parseDouble(Helpers.getKvsDefault(
                kvsClient,
                "pt-document-idf",
                "" + stemmedQueryTerm,
                "value",
                "0.0"
            ));

            // Next, if the urls aren't empty, then try to get a list of them
            if(!urlsString.isEmpty()) {
                // Split it, and for every url, add the hash to our accumulator
                for(String urlItem : urlsString.split(",")) {
                    String urlHash = Hasher.hash(urlItem);
                    urlHashes.put(urlHash, urlItem);
                    idfValues.put(urlHash, termIdfValue);
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

            // Parse the page rank and serializer
            if(pageRankTermFreqStr.contains("|")) {
                String[] documentTuple = pageRankTermFreqStr.split("|", 3);

                String pageRankStrPart = documentTuple[0];
                String tfStrPart = documentTuple[1];
                String text = documentTuple[2];

                SearchDocument parsedDoc = new SearchDocument(
                    "" + pageRankStrPart,
                    "" + urlHashes.get(urlHash),
                    "" + text,
                    "" + pageRankStrPart
                );

                documents.put(urlHash, parsedDoc);

                // Now put the values into their maps
//                documentsTermFreq.put(urlHash, Helpers.deserializeHashMap(tfStrPart));
//                pageRank.put(urlHash, Double.parseDouble(pageRankStrPart));
            }
        }

        /**
         * II. For each document, calculate cosine similarity
         */

        // 1. For every page from the index


        /**
         * For every document found
         */
        for (String urlHash : documents.keySet()) {
            // Get the document in question
            SearchDocument currentDocument = documents.get(urlHash);
            Map<String, Integer> docTF = currentDocument.getTermFreq();
            //
            HashMap<String, Double> queryVector = vectorize(query, idfValues);
            double cosineSimilarity = computeCosineSimilarity(queryVector, docTF);

            SearchResult sr = new SearchResult(
                currentDocument,
                cosineSimilarity
            );

            results.add(sr);
        }

        results.sort((r1, r2) -> Double.compare(r2.getScore(), r1.getScore())); // Sort in descending order
        return results.subList(fromIndex, toIndex);
    }



    public static void main(String args[]) throws IOException {
        // Initialize port
        port(Integer.parseInt(args[0]));
        // Initialize Mock data file path
        String mockDataFile = args[1];

        // Establish connection to host
        KVSClient kvsClient = new KVSClient("localhost:8000");

        staticFiles.location("static");

        post("/api/search", (req,res) -> {
            String searchTerm = req.queryParams("term");
            res.header("X-SearchTerm", searchTerm);
            res.type("application/json");
            return mockSearch(mockDataFile);
        });

        get("/api/search", (req,res) -> {
            String searchTerm = req.queryParams("term");
            res.header("X-SearchTerm", searchTerm);
            res.type("application/json");
            return mockSearch(mockDataFile);
        });
    }
}