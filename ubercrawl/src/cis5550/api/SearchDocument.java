/**
 * A helper class whose purpose is to help store
 * the documents being searched. It stores the url,
 * text, pagerank, and term frequencies.
 *
 * @author Sergio Garcia <gsergio@seas.upenn.edu>
 */
package cis5550.api;

import cis5550.tools.Hasher;
import cis5550.tools.Helpers;

import java.util.HashMap;


public class SearchDocument {
    private String id;
    private String url;
    private String text;
    private String pagerank;
    private HashMap<String, Integer> termFrequencies;

    public SearchDocument(String pagerank, String url, String text, String termFrequencies) {
        this.pagerank = pagerank;
        this.text = text;
        this.url = url;
        this.termFrequencies = Helpers.deserializeHashMap(termFrequencies);
    }

    public String getText() {
        return this.text;
    }

    public String getId() {
        return Hasher.hash(this.url);
    }

    public String getUrl() {
        return this.url;
    }

    public double getPagerank() {
        try {
            return Double.parseDouble(this.pagerank);
        } catch (Exception e) {
            return 0;
        }
    }

    public HashMap<String, Integer> getTermFreq() {
        return this.termFrequencies;
    }

    public String toString() {
        return "<SearchDocument>: { id: '" + getId() + "', url: '" +  getUrl() + "', text: '" + getText() + "', pagerank: '" + getPagerank() + "'}";
    }

    public String toJsonAttr() {
        return "\"id\": \"" + getId() + "\", \"url\": \"" +  getUrl() + "\", \"text\": \"" + getText() + "\", \"pagerank\": \"" + getPagerank() + "\"";
    }
}