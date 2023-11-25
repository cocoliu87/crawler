package cis5550.tools;

/**
 * A helper class that will be helpful to store
 * the document being searched, the search score,
 * sorting, etc.
 */
public class SearchResult {
    private TFIDFDocument document;
    private double score;

    public SearchResult(TFIDFDocument document, double score) {
        this.document = document;
        this.score = score;
    }

    public TFIDFDocument getDocument() {
        return document;
    }

    public double getScore() {
        return score;
    }
}