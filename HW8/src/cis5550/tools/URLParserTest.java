package cis5550.tools;

import org.junit.Assert;
import org.junit.Test;

public class URLParserTest {
    @Test
    public void parseURL() {
        String url = "https://www.google.com/mail/inbox.html";
        String[] strs = URLParser.parseURL(url);
        Assert.assertEquals(4, strs.length);
    }
}
