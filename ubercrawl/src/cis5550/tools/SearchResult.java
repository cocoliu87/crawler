package cis5550.tools;

/**
 * A helper class that will be helpful to store
 * the document being searched, the search score,
 * sorting, etc.
 */
public class SearchResult {
    private Document document;
    private double score;

    public SearchResult(Document document, double score) {
        this.document = document;
        this.score = score;
    }

    public Document getDocument() {
        return document;
    }

    public double getScore() {
        return score;
    }
}