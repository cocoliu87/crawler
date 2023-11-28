/**
 * Simple TF/IDF search proof of concept.
 *
 * The purpose of this program was to learn, develop and test the
 * TF/IDF search algorithm. It does not play an active part of the
 * overall project. Two data processing jobs and the API are based
 * off of this exercise. It stays here for reference.
 *
 * @author Sergio Garcia <gsergio@seas.upenn.edu>
 */
package cis5550.tools;

import cis5550.external.PorterStemmer;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;


public class TFIDFSearch {
    private List<TFIDFDocument> documents;

    // Document document -> tfMap
    private Map<String, Map<String, Integer>> termFrequencies;

    // Word -> Count
    private Map<String, Integer> documentFrequencies;

    // Word -> Double (idf value)
    private Map<String, Double> idf;

    // Taken from pythons stop words library, also found in other nlp libraries.
    private final List<String> stopWords = List.of("a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "arent", "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by", "cant", "cannot", "could", "couldnt", "did", "didnt", "do", "does", "doesnt", "doing", "dont", "down", "during", "each", "few", "for", "from", "further", "had", "hadnt", "has", "hasnt", "have", "havent", "having", "he", "hed", "hell", "hes", "her", "here", "heres", "hers", "herself", "him", "himself", "his", "how", "hows", "i", "id", "ill", "im", "ive", "if", "in", "into", "is", "isnt", "it", "its", "its", "itself", "lets", "me", "more", "most", "mustnt", "my", "myself", "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves", "out", "over", "own", "same", "shant", "she", "shed", "shell", "shes", "should", "shouldnt", "so", "some", "such", "than", "that", "thats", "the", "their", "theirs", "them", "themselves", "then", "there", "theres", "these", "they", "theyd", "theyll", "theyre", "theyve", "this", "those", "through", "to", "too", "under", "until", "up", "very", "was", "wasnt", "we", "wed", "well", "were", "weve", "were", "werent", "what", "whats", "when", "whens", "where", "wheres", "which", "while", "who", "whos", "whom", "why", "whys", "with", "wont", "would", "wouldnt", "you", "youd", "youll", "youre", "youve", "your", "yours", "yourself", "yourselves");

    public TFIDFSearch(List<TFIDFDocument> documents) {
        this.documents = documents;
        this.documentFrequencies = new HashMap<>();
        this.termFrequencies = new HashMap<>();
        this.idf = new HashMap<>();
        preprocess();
    }

    private String stemWord(String term) {
        PorterStemmer porterStemmer = new PorterStemmer();
        char[] charArray = term.toLowerCase().trim().toCharArray();
        porterStemmer.add(charArray, charArray.length);
        porterStemmer.stem();
        return porterStemmer.toString();
    }

    private String[] getWords(String document) {
        String[] firstSet = document
            .toLowerCase()
            .replaceAll("\\p{Punct}", "")
            .split("\\s+");

        return Arrays
            .stream(firstSet)
            .filter(word -> !stopWords.contains(word))
            .filter(word -> !word.isEmpty())
            .map(this::stemWord)
            .toArray(String[]::new);
    }

    private void preprocess() {
        for (TFIDFDocument document : documents) {
            Map<String, Integer> tf = new HashMap<>();
            String[] words = getWords(document.getText());

            // Compute term frequencies for the document
            for (String word : words) {
                tf.put(word, tf.getOrDefault(word, 0) + 1);
            }

            // Update document frequencies
            for (String word : tf.keySet()) {
                documentFrequencies.put(word, documentFrequencies.getOrDefault(word, 0) + 1);
            }

            // Update term frequencies
            termFrequencies.put(document.getId(), tf);
        }

        // Compute IDF values
        int numDocuments = documents.size();
        for (String term : documentFrequencies.keySet()) {
            int df = documentFrequencies.get(term);
            idf.put(term, Math.log((double) numDocuments / (double) (df + 1)));
        }
    }

    public Map<String, Double> vectorize(String query) {
        Map<String, Double> tfidfVector = new HashMap<>();
        String[] words = getWords(query);

        // Compute term frequencies for the query
        Map<String, Integer> tf = new HashMap<>();
        for (String word : words) {
            tf.put(word, tf.getOrDefault(word, 0) + 1);
        }

        // Compute TF-IDF values for the query
        for (String term : tf.keySet()) {
            double tfidf = (1 + Math.log(tf.get(term))) * idf.getOrDefault(term, 0.0);
            tfidfVector.put(term, tfidf);
        }

        return tfidfVector;
    }

    private double computeCosineSimilarity(Map<String, Double> vector1, Map<String, Integer> vector2) {
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

    public List<TFIDFSearchResult> search(String query, int fromIndex, int toIndex) {
        Map<String, Double> queryVector = vectorize(query);
        List<TFIDFSearchResult> results = new ArrayList<>();

        for (TFIDFDocument document : documents) {
            Map<String, Integer> docTF = termFrequencies.get(document.getId());
            double cosineSimilarity = computeCosineSimilarity(queryVector, docTF);

            results.add(new TFIDFSearchResult(document, cosineSimilarity));
        }

        results.sort((r1, r2) -> Double.compare(r2.getScore(), r1.getScore())); // Sort in descending order
        return results.subList(fromIndex, toIndex);
    }

    public static List<TFIDFDocument> loadSongList() {
        List<TFIDFDocument> documents = new ArrayList<>();
        try(Scanner scanner = new Scanner(new File("./ubercrawl/static/songdata.csv"))) {
            //Read line
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] tokens = Arrays.stream(line.split(","))
                    .map(col -> col.replace("\"", ""))
                    .toArray(String[]::new);

                StringBuilder sb = new StringBuilder();
                sb.append(tokens[0]).append(",").append(tokens[1]).append(",").append(tokens[3]);

                // Add new song to documents
                documents.add(
                    new TFIDFDocument(
                        tokens[2], // url
                        sb.toString() // body
                    )
                );
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return documents;
    }

    public static void main(String[] args) {
        List<TFIDFDocument> documents = loadSongList();

        TFIDFSearch tfIdfSearch = new TFIDFSearch(documents);

        String query = "Marley";
        List<TFIDFSearchResult> searchResults = tfIdfSearch.search(query, 0, 40);

        System.out.println("Top search results for query \"" + query + "\":");
        for (TFIDFSearchResult result : searchResults) {
            System.out.println(result.getDocument().getText());
            System.out.println(result.getDocument().getUrl());
            System.out.println(result.getDocument().getId());
            System.out.println(result.getScore());
        }
    }
}