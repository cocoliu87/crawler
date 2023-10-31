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
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class Crawler {
    static final String crawlerTable = "pt-crawl", hostsTable = "hosts", timeCol = "time", robotCol = "robot", agent = "cis5550-crawler";
    static Map<String, String> robots = new HashMap<>();
    public static void run(cis5550.flame.FlameContext ctx, String[] args) throws Exception {
        ctx.output(args.length == 1? "OK\n" : "Error: Crawler expects one argument\n");

        FlameRDDImpl urlQueue = (FlameRDDImpl) ctx.parallelize(Arrays.asList(args));
        AtomicReference<Double> crawlInterval = new AtomicReference<>(0.1);
        while (urlQueue.count() != 0) {
            urlQueue = (FlameRDDImpl) urlQueue.flatMap(oriUrl -> {
                KVSClient client = ctx.getKVS();
                String url = addPort(oriUrl);
                Row dr = new Row(url);
                dr.put("original-url", oriUrl);
                client.putRow("debug-table", dr);
                //if (!isValidUrl(url)) return List.of();
                if (url.isEmpty()) return List.of();

                if (client.getRow(crawlerTable, Hasher.hash(url)) != null) {
                    return List.of();
                }
                String[] domains = URLParser.parseURL(url);
                String host = domains[0] + "://" + domains[1];
                if (!robots.containsKey(host)) {
                    getCheckRobot(host, client, hostsTable);
                }

                List<RobotRules> rules = validateRobotRules(host);

                RobotRules robotRules = null;

                for (RobotRules rule: rules) {
                    if (rule.isValidAgent()) {
                        robotRules = rule;
                        break;
                    }
                }

                // if rules are not null, we need respect the rules
                if (robotRules != null) {
                    if (robotRules.CrawlDelay != 0)
                        crawlInterval.set(robotRules.CrawlDelay);
                    // agent is not valid to crawl
                    if (!robotRules.isValidAgent()) {
                        return List.of();
                    }

                    String allowed = "", disallowed = "";
                    int pAllowed = -1, pDisallowed = -1;
                    AllowedRule allowedRule = robotRules.getAllowedRule();
                    DisallowedRule disallowedRule = robotRules.getDisallowedRule();
                    if (allowedRule != null) {
                        allowed = allowedRule.AllowedHost;
                        pAllowed = allowedRule.Priority;
                    }
                    if (disallowedRule != null) {
                        disallowed = disallowedRule.DisallowedHost;
                        pDisallowed = disallowedRule.Priority;
                    }
                    boolean matchAllowed = !allowed.isEmpty() && (domains[1].contains(allowed) || domains[3].startsWith(allowed)),
                            matchDisallowed = !disallowed.isEmpty() && (domains[1].contains(disallowed) || domains[3].startsWith(disallowed));
                    // TODO: check if empty rules is allowing all
                    // pDisallowed == 1, or pDisallowed = -1
                    if (pAllowed == 0) {
                        if (matchAllowed)
                            return crawlPage(url, client);
                        else
                            return List.of();
                    }
                    // pAllowed == 1 or pAllowed = -1
                    if (pDisallowed == 0) {
                        if (matchDisallowed)
                            return List.of();
                        else {
                            if (pAllowed == 1) {
                                if (matchAllowed) {
                                    return crawlPage(url, client);
                                } else {
                                    return List.of();
                                }
                            } else {
                                return crawlPage(url, client);
                            }
                        }
                    }

                    //both pAllowed and pDisallowed == -1 (unset)
                    return crawlPage(url, client);
                } else {
                    // not found robot file
                    return crawlPage(url, client);
                }
            });
            Thread.sleep((long) (crawlInterval.get()*1000));
        }
    }

    public static List<String> crawlPage(String url, KVSClient client) throws IOException, URISyntaxException {
        String[] blocked = new String[]{".jpg", ".jpeg", ".gif", ".png", ".txt"};
        for (String b: blocked) {
            if (url.endsWith(b)){
                return List.of();
            }
        }
        HttpURLConnection conn = (HttpURLConnection) (new URI(url).toURL()).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("HEAD");
        conn.connect();
        int code = conn.getResponseCode();
        // redirect to url fetched from Location
        if (new HashSet<>(List.of(301, 302, 303, 307, 308)).contains(code)) {
            String redirect = conn.getHeaderField("Location");
            Row rr = new Row(Hasher.hash(url));
            rr.put("url", url);
            rr.put("contentType", conn.getContentType());
            rr.put("length", String.valueOf(conn.getContentLength()));
            rr.put("responseCode", String.valueOf(code));
            conn.disconnect();
            return List.of(redirect);
        }

        String contentType = conn.getContentType();
        int length = conn.getContentLength();
        conn.disconnect();
        if (code != 200 || !contentType.equals("text/html")) {
            Row rr = new Row(Hasher.hash(url));
            rr.put("url", url);
            rr.put("contentType", conn.getContentType());
            rr.put("length", String.valueOf(conn.getContentLength()));
            rr.put("responseCode", String.valueOf(code));
            client.putRow(crawlerTable, rr);
            return List.of();
        }

        String hostName = URLParser.parseURL(url)[1];
        byte[] timeBytes = client.get(hostsTable, hostName, timeCol);
        if (timeBytes != null) {
            String lastTime = new String(client.get(hostsTable, hostName, timeCol), StandardCharsets.UTF_8);
            if (!lastTime.isEmpty()) {
                long lastTimeLong = Long.parseLong(lastTime);
                if (lastTimeLong > 0 && System.currentTimeMillis() - lastTimeLong < 100) {
                    return List.of(url);
                }
            }
        }
        client.put("hosts", hostName, "time", String.valueOf(System.currentTimeMillis()));

        conn = (HttpURLConnection) (new URI(url).toURL()).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", agent);
        conn.setRequestProperty("Content-Type", "text/html");
        conn.connect();
        int responseCode = conn.getResponseCode();
        List<String> links = new ArrayList<>();
        if (responseCode == 200) {
            InputStream input = conn.getInputStream();
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Row r = new Row(Hasher.hash(url));
            r.put("url", url);
            r.put("page", text);
            r.put("contentType", contentType);
            r.put("length", String.valueOf(length));
            r.put("responseCode", String.valueOf(code));

            client.putRow(crawlerTable, r);
            input.close();
            links = processUrls(getAllUrls(text), url);
        }
        conn.disconnect();
        return links;
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
        String contentType = conn.getContentType();
        int length = conn.getContentLength();
        conn.disconnect();
        if (code != 200 || !contentType.equals("text/html"))
            return map;

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
    public static boolean isValidUrl(String url) {
        if (url == null || url.isEmpty())
            return false;
        try {
            URL obj = new URL(url);
            obj.toURI();
            return true;
        } catch (MalformedURLException e) {
            return false;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static String addPort(String url) throws IOException {
        String[] hosts = URLParser.parseURL(url);
        String protocol = hosts[0], hostName = hosts[1], port = hosts[2], subDomains = hosts[3];
        if (protocol == null || hostName == null || hostName.isEmpty()) {
            return "";
        }
        if (protocol.equals("http") && port == null) port = "80";
        else if (protocol.equals("https") && port == null) port = "443";
        return protocol + "://" + hostName + ":" + port + subDomains;
    }

    /**
     * Check if the host has robots file to claim rules for crawling. This method checks the file and push the content to
     * RDD table and saves to local cache
     * @param host the url's host name
     * @param client the KVS client that will be used for RDD ops
     * @param tableName the RDD table name
     * @throws URISyntaxException
     * @throws IOException
     */
    public static void getCheckRobot(String host, KVSClient client, String tableName) throws URISyntaxException, IOException {
/*        if (!(host)) {
            return;
        }*/
        String url = host + "/robots.txt";
        HttpURLConnection conn = (HttpURLConnection) (new URI(url).toURL()).openConnection();
        conn.setRequestMethod("GET");
        conn.connect();
        int code = conn.getResponseCode();
        if (code == 200) {
            InputStream input = conn.getInputStream();
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Row r = new Row(Hasher.hash(url));
            r.put(robotCol, url);

            client.putRow(tableName, r);
            input.close();

            robots.put(host, text);
        }
    }

    /**
     * Check if the robots rules
     * @param host the URL hostname
     * @return
     */
    public static List<RobotRules> validateRobotRules(String host) {
        if (!robots.containsKey(host)) {
            return List.of();
        }
        String[] policies = robots.get(host).split(System.lineSeparator());
        List<RobotRules> robotRules = new ArrayList<>();
        RobotRules rule = new RobotRules();
        for (String policy: policies) {
            if (policy.contains(":")) {
                String[] strs = policy.split(":");
                String key = strs[0].strip().toLowerCase();
                if (key.equals("user-agent")) {
                    if (rule.Set) {
                        robotRules.add(rule);
                        rule = new RobotRules();
                    }
                    rule.Set = true;

                    String value = strs[1].strip();
                    rule.setValidAgent(value.equals("*") || value.equals(agent));
                }
                if (key.equals("disallow")) {
                    int p = 0;
                    if (rule.getAllowedRule() != null) {
                        p = 1;
                    }
                    rule.setDisallowedRule(new DisallowedRule(strs[1].strip(), p));
                }
                if (key.equals("allow")) {
                    int p = 0;
                    if (rule.getDisallowedRule() != null) {
                        p = 1;
                    }
                    rule.setAllowedRule(new AllowedRule(strs[1].strip(), p));
                }
            }
        }
        robotRules.add(rule);
        return robotRules;
    }
}
