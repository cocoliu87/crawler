/**
 * A helper class for the simple TF/IDF search proof of concept.
 *
 * The purpose of this program was to learn, develop and test the
 * TF/IDF search algorithm. It does not play an active part of the
 * overall project. Two data processing jobs and the API are based
 * off of this exercise. It stays here for reference.
 *
 * @author Sergio Garcia <gsergio@seas.upenn.edu>
 */
package cis5550.tools;

public class TFIDFDocument {
    private String id;
    private String url;
    private String text;

    public TFIDFDocument(String url, String text) {
        this.id = Hasher.hash(url);
        this.text = text;
        this.url = url;
    }

    public TFIDFDocument(String id, String url, String text) {
        this.id = id;
        this.text = text;
        this.url = url;
    }

    public String getText() {
        return text;
    }

    public String getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }
}