package cis5550.api;

import cis5550.tools.TFIDFDocument;

/**
 * A helper class that will be helpful to store
 * the document being searched, the search score,
 * sorting, etc.
 */
public class SearchResult {
    private SearchDocument document;
    private double cosineSimilarity;


    public SearchResult(SearchDocument doc, double cosineSimilarity) {
        this.document = doc;
        this.cosineSimilarity = cosineSimilarity;
    }

    public double getScore() {
        return cosineSimilarity + document.getPagerank();
    }
}