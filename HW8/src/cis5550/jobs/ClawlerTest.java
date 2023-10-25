package cis5550.jobs;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClawlerTest {

    @Test
    public void getAllUrls_Matches() throws IOException {
        String page =
            "<a href=\"http://simple.crawltest.cis5550.net/\" >CIS5550 Crawler Test Page</a> " +
            "<h3>sat down beside Hippolyte and wrinkling his forehead began talkingto him</h3> " +
            "<h1>is exquisite</h1>when he discusses politics-you should see <a href=\"/UJ2p.html\">hisgravity!\"He</a>" +
            "<a href=\"http://www.google.com\" >Google</a>" +
            "<a>Hello World!</a>";
        List<String> links = Crawler.getAllUrls(page);
        Assert.assertEquals(3, links.size());
    }

    @Test
    public void processUrls_Success() {
        List<String> urls = new ArrayList<>();
        urls.add("#abc");
        urls.add("blah.html#test");
        urls.add("../blubb/123.html");
        urls.add("/one/two.html");
        urls.add("../../blubb/456.html");

        String oriUrl = "https://foo.com:8000/bar/one/xyz.html";
        List<String> processed = Crawler.processUrls(urls, oriUrl);
        Assert.assertEquals(4, processed.size());
    }
}
