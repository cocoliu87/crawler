package cis5550.tools;

import java.util.List;
import java.util.Map;

public class HttpCrawlResponse {
    public String content;
    public byte[] data;
    public Map<String, List<String>> headers;
    public int httpCode;

    public String toString() {
        return "(HttpCrawlResponse) Content: " + content +
                ", headers: " + headers +
                ", httpCode: " + httpCode;
    }
}
