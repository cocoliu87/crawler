/**
 * A helper class whose purpose is to help represent
 * a search result, which includes a newly-calculated
 * cosine similarity value.
 *
 * It also helps with simplifying the sorting of results.
 *
 * @author Sergio Garcia <gsergio@seas.upenn.edu>
 */

package cis5550.api;

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

    /**
     * This is where the cosine similarity value, and page rank
     * are summed and used for result ranking (sorting).
     * @return
     */
    public double getScore() {
        return cosineSimilarity + document.getPagerank();
    }
}