package cis5550.tools;

/**
 * A helper class that will be helpful to store
 * the document being searched, the search score,
 * sorting, etc.
 */
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