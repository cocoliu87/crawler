package cis5550.jobs;

import cis5550.flame.FlameRDDImpl;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Hasher;
import cis5550.tools.URLParser;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.util.*;

public class Crawler {
    public static void run(cis5550.flame.FlameContext ctx, String[] args) throws Exception {
        ctx.output(args.length == 1? "OK\n" : "Error: Crawler expects one argument\n");

        FlameRDDImpl urlQueue = (FlameRDDImpl) ctx.parallelize(Arrays.asList(args));

        String crawlerTable = "pt-crawl";
        while (urlQueue.count() != 0) {
            urlQueue = (FlameRDDImpl) urlQueue.flatMap(url -> {
                KVSClient client = ctx.getKVS();
                if (client.getRow(crawlerTable, Hasher.hash(url)) != null) {
                    return List.of();
                }

                Map<Integer, String> head = headCheckCode(url);
                int headCode = 0;
                String redirect = "";
                for (Map.Entry<Integer, String> entry: head.entrySet()){
                    headCode = entry.getKey();
                    redirect = entry.getValue();
                }
                //if (code != 200 && !contentType.equals("text/html")) {
                if (headCode < 0) {
                    return List.of();
                }

                // redirect to url fetched from Location
                if (new HashSet<>(List.of(301, 302, 303, 307, 308)).contains(headCode)) {
                    return List.of(redirect);
                }

                String hostName = URLParser.parseURL(url)[1];
                byte[] timeBytes = client.get("hosts", hostName, "time");
                if (timeBytes != null) {
                    String lastTime = new String(client.get("hosts", hostName, "time"), StandardCharsets.UTF_8);
                    if (!lastTime.isEmpty()) {
                        long lastTimeLong = Long.parseLong(lastTime);
                        if (lastTimeLong > 0 && System.currentTimeMillis() - lastTimeLong < 100) {
                            return List.of(url);
                        }
                    }
                }
                client.put("hosts", hostName, "time", String.valueOf(System.currentTimeMillis()));

                HttpURLConnection conn;
                conn = (HttpURLConnection) (new URI(url).toURL()).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "cis5550-crawler");
                conn.setRequestProperty("Content-Type", "text/html");
                conn.connect();
                int responseCode = conn.getResponseCode();
                List<String> links = new ArrayList<>();
                if (responseCode == 200) {
                    InputStreamReader input = new InputStreamReader((InputStream) conn.getContent());
                    BufferedReader reader = new BufferedReader(input);
                    StringBuilder page = new StringBuilder();
                    String line;
                    do {
                        line = reader.readLine();
                        page.append(line);
                    } while (line != null);
                    Row r = new Row(Hasher.hash(url));
                    r.put("url", url);
                    r.put("page", page.toString());

                    client.putRow(crawlerTable, r);
                    input.close();
                    links = processUrls(getAllUrls(page.toString()), url);
                }
                return links;
            });
            Thread.sleep(100);
        }
    }

    public static Map<Integer, String> headCheckCode(String url) throws IOException, URISyntaxException {
        // .jpg, .jpeg, .gif, .png, or .txt are not allowed
        String[] blocked = new String[]{".jpg", ".jpeg", ".gif", ".png", ".txt"};
        Map<Integer, String> map = new HashMap<>();
        for (String b: blocked) {
            if (url.endsWith(b)){
                map.put(-1, "");
                return map;
            }
        }
        HttpURLConnection conn = (HttpURLConnection) (new URI(url).toURL()).openConnection();
        conn.setRequestMethod("HEAD");
        conn.connect();
        int code = conn.getResponseCode();
        //String contentType = conn.getContentType();
        conn.disconnect();
        map.put(code, conn.getHeaderField("Location"));
        return map;
    }

    public static List<String> getAllUrls(String page) throws IOException {
        List<String> links = new ArrayList<>();
        Reader reader = new StringReader(page);
        HTMLEditorKit.Parser parser = new ParserDelegator();
        parser.parse(reader, new HTMLEditorKit.ParserCallback(){
            public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
                if (t == HTML.Tag.A) {
                    Object link = a.getAttribute(HTML.Attribute.HREF);
                    if (link != null) {
                        links.add(String.valueOf(link));
                    }
                }
            }
        }, true);

        reader.close();
        return links;
    }

    public static List<String> processUrls(List<String> urls, String host) {
        List<String> processed = new ArrayList<>();

        String[] hosts = URLParser.parseURL(host);
        String protocol = hosts[0], hostName = hosts[1], port = hosts[2], subDomains = hosts[3];
        if (protocol.equals("http") && port == null) port = "80";
        else if (protocol.equals("https") && port == null) port = "443";

        for (String url: urls) {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                processed.add(url);
            } else {
                StringBuilder newUrl = new StringBuilder(protocol).append("://").append(hostName).append(":").append(port);
                if (url.contains("#")) {
                    url = url.split("#")[0];
                    if (url.isEmpty()) {
                        // TODO: this should be same as current url
                        // confirm if skip or still add it (possible infinite search)
                        continue;
                    } else {
                        String[] domains = subDomains.split("/");
                        domains[domains.length-1] = url;
                        for (String d: domains) if (!d.isEmpty()) newUrl.append("/").append(d);
                    }
                } else if (url.startsWith("/")) {
                    newUrl.append(url);
                } else if (url.startsWith("..")) {
                    String[] domains = subDomains.split("/");
                    String[] strs = url.split("/");
                    int count = 0;
                    String p = strs[0];
                    while (p.equals("..")) {
                        count++;
                        p = strs[count];
                    }
                    for (int i = 0; i < domains.length-1-count; i++) {
                        if (!domains[i].isEmpty()) newUrl.append("/").append(domains[i]);
                    }
                    for (; count < strs.length; count++) {
                        if (!strs[count].isEmpty()) newUrl.append("/").append(strs[count]);
                    }
                }
                processed.add(newUrl.toString());
            }

        }
        return processed;
    }
}
