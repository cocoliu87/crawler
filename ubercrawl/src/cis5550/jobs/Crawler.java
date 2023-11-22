package cis5550.jobs;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import cis5550.flame.FlameContext;
import cis5550.flame.FlameRDD;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Hasher;
import cis5550.tools.Logger;
import cis5550.tools.URLParser;

public class Crawler {
    private static final Pattern LINK_PATTERN = Pattern.compile("<a\\s+(?:[^>]*?\\s+)?href=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Set<String> seenUrls = ConcurrentHashMap.newKeySet();
    private static final int MAX_PERMITS = 10; // You can adjust this value as needed
    private static final Set<String> globalUrlSet = ConcurrentHashMap.newKeySet();
    private static final int TIMEOUT = 10000;
    private static final String USER_AGENT = "cis5550-crawler";
    private static final int MAX_URLS_TO_EXTRACT = 100;
    private static final HashSet<Integer> s = new HashSet<Integer>(Arrays.asList(301, 302, 303, 307, 308));

    private static final Logger logger = Logger.getLogger(Crawler.class);
    public static void run(FlameContext ctx, String[] arr) {

        String kvs_master = ctx.getKVS().getCoordinator();
//        ctx.setConcurrencyLevel(5);
        String base;

        List<String> seed = new LinkedList<String>();

        if (arr.length >= 1 && arr[0].equals("seed")) {
            for (int i=1; i<arr.length; i++) {
                base = URLParser.parseURL(arr[i])[0];
                seed.add(normalize(arr[i], ""));
            }
        }

        ctx.output("OK?");

        FlameRDD urlQueue = null;

        try {
            urlQueue = ctx.parallelize(seed);
        } catch (Exception e1) {
            e1.printStackTrace();
        }


        try {
            int count = 0;
            while (count < 100 && urlQueue.count() != 0) {
                System.out.println(count+"-----------------------------------");
                count += 1;

                urlQueue = urlQueue.flatMap(u-> {
                    Set<String> new_url_list = new HashSet<String>();
                    try {
                        KVSClient kvs = new KVSClient(kvs_master);
                        String hashKey = Hasher.hash(u);

                        if (kvs.existsRow("crawled-url", hashKey)) {
                            return new_url_list;
                        }

                        String[] parsed_url = URLParser.parseURL(u);

                        Row host_row = kvs.getRow("hosts", parsed_url[1]);

                        if (host_row != null) {
                            String robots_txt = host_row.get("robots_txt");

                            if (robots_txt == null) {
                                robots_txt = "";
                            }

                            boolean flag = false;
                            long time_interval = 10;

                            for (String line: robots_txt.split("\n")) {
                                String[] split = line.replace(" ","").toLowerCase().split(":");
                                if (split.length != 2) {
                                    continue;
                                }

                                if (split[0].equals("user-agent")) {
                                    if (split[1].equals("cis5550-crawler") || split[1].equals("*")) {
                                        flag = true;
                                    } else {
                                        flag = false;
                                    }
                                }

                                else if (flag) {
                                    if (split[0].equals("allow")) {
                                        if (parsed_url[3].startsWith(split[1])) {
//				    						System.out.println("allow");
                                            break;
                                        }
                                    }
                                    if (split[0].equals("disallow")) {
                                        if (parsed_url[3].startsWith(split[1])) {
//				    						System.out.println("disallow");
                                            return new_url_list;
                                        }
                                    }
                                    if (split[0].equals("crawl-delay")) {
                                        time_interval = (long) Double.parseDouble(split[1])*1000;
//				    					System.out.println("delay");
                                    }
                                }
                            }

                            if (System.currentTimeMillis() - Long.parseLong(new String(host_row.get("last_accessed_time"))) < time_interval) {
                                new_url_list.add(u);
                                return new_url_list;
                            }

                        } else {
                            host_row = new Row(parsed_url[1]);
                            HttpURLConnection robot_con = (HttpURLConnection)(new URL(parsed_url[0]+"://"+parsed_url[1]+":"+parsed_url[2]+"/robots.txt")).openConnection();
                            robot_con.setConnectTimeout(5000);
                            robot_con.setReadTimeout(5000);
                            robot_con.setRequestMethod("GET");
                            robot_con.setDoOutput(true);
                            robot_con.setRequestProperty("User-Agent", "cis5550-crawler");
                            robot_con.setInstanceFollowRedirects(false);
                            robot_con.connect();
                            if (robot_con.getResponseCode() == 200) {
                                BufferedReader robot_r = new BufferedReader(new InputStreamReader(robot_con.getInputStream()));
                                String robots_txt = "";
                                while (true) {
                                    String l = robot_r.readLine();
                                    if (l == null)
                                        break;
                                    robots_txt = robots_txt + "\n" + l;
                                }
                                host_row.put("robots_txt", robots_txt);
                            }
                        }

                        host_row.put("last_accessed_time", String.valueOf(System.currentTimeMillis()));
                        kvs.putRow("hosts", host_row);
                        HttpURLConnection con = (HttpURLConnection)(new URL(u)).openConnection();
                        con.setReadTimeout(5000);
                        con.setConnectTimeout(5000);
                        con.setRequestMethod("HEAD");
                        con.setDoOutput(true);
                        con.setInstanceFollowRedirects(false);
                        con.setRequestProperty("User-Agent", "cis5550-crawler");
                        con.connect();
                        Row row = new Row(hashKey);
                        row.put("url", u);
                        int code = con.getResponseCode();
                        row.put("responseCode", String.valueOf(code));
                        Map<String, List<String>> header = con.getHeaderFields();
                        Map<String, String> lower_header = new HashMap<String, String>();
                        for (String key: header.keySet()) {
                            if (key != null && header.get(key).size() > 0) {
                                lower_header.put(key.toLowerCase(), header.get(key).get(0));
                            }
                        }

                        if (lower_header.containsKey("content-encoding") && lower_header.get("content-encoding").equals("gzip")) {
                            return new_url_list;
                        }
                        if (lower_header.containsKey("content-language") && !lower_header.get("content-language").startsWith("en")) {
                            return new_url_list;
                        }
                        if (lower_header.containsKey("content-type")) {
                            row.put("contentType", lower_header.get("content-type").toString());
                        }
                        if (lower_header.containsKey("content-length")) {
//					    	row.put("length", lower_header.get("content-length").toString());
                            row.put("length", con.getHeaderField("Content-Length"));
                        }

                        if (code == 200) {
                            Connection.Response response = Jsoup.connect(u)
                                    .userAgent(USER_AGENT)
                                    .timeout(TIMEOUT)
                                    .followRedirects(false)
                                    .execute();

                            // Check the status code and content type
                            int statusCode = response.statusCode();
                            String contentType = response.contentType();

                            if (statusCode == 200 && contentType != null && contentType.contains("text/html")) {
                                Document doc = response.parse();

                                String bodyText = doc.text();
                                String limitedBodyText = bodyText.substring(0, Math.min(bodyText.length(), 10000));

                                // Extract the main content based on the CSS selector, if necessary
                                Element mainContent = doc.select("#mw-content-text > div").first();
                                if (mainContent != null) {
                                    limitedBodyText = mainContent.text().substring(0, Math.min(mainContent.text().length(), 10000));
                                }

                                // Save the truncated text content
                                row.put("page", limitedBodyText); // Assuming 'row' is a Map-like structure

                                Elements links = doc.select("a[href]");

                                for (Element link : links) {
                                    String linkHref = link.attr("href");
                                    if (isValidLink(linkHref)){
                                        String new_url = normalize(linkHref, u);
//                                        if (new_url.contains("en.wikipedia.org")) {
                                        if (!new_url.isEmpty() && !kvs.existsRow("frontier", Hasher.hash(new_url))) {
                                            // Your URL filtering conditions here
                                            new_url_list.add(new_url);
                                            kvs.put("frontier", Hasher.hash(new_url), "url", new_url);
                                        }
//                                        }
                                    }
                                }
                            }
                        } else if (s.contains(code)){
                            if (!lower_header.get("location").equals(u)) {
                                new_url_list.add(normalize(lower_header.get("location"), u));
                            }
                        }
                        kvs.putRow("pt-crawl", row);
                        kvs.put("crawled-url", hashKey, "url", u);
                        System.gc();
                    } catch (Exception e) {
                        logger.error("Error in flatmap: " + u +" "+e.toString());
                    }
                    return new_url_list;
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String normalize(String s, String base) {

        if (s.indexOf("#") != -1) {
            s = s.substring(0, s.indexOf("#"));
        }

        if (s.equals("")) {
            return base;
        }
        String[] parse1 = URLParser.parseURL(s);
        String[] parse2 = URLParser.parseURL(base);
        String protocal1 = parse1[0];
        String host1 = parse1[1];
        String port1 = parse1[2];
        String detail1 = parse1[3];
        String protocal2 = parse2[0];
        String host2 = parse2[1];
        String port2 = parse2[2];
        String detail2 = parse2[3];
        String ret = "";
        if (s.startsWith("/")) {
            return protocal2 + "://" + host2 + (protocal2.contains("https") ? ":443" : ":80") + s;
        }
        if (protocal1 != null){
            if (protocal1.equals("http") || protocal1.equals("https")) {
                ret += protocal1 + ":";
            } else {
                return "";
            }
        } else if (protocal2 != null) {
            if (protocal2.equals("http") || protocal2.equals("https")) {
                ret += protocal2 + ":";
            } else {
                System.out.println("something wrong2, base: "+base+", new_uri: "+s);
                return "";
            }
        } else {
            System.out.println("something wrong3, base: "+base+", new_uri: "+s);
            return "";
        }
        if (host1 != null) {
            ret += "//" + host1;
        } else if (host2 != null){
            ret += "//" + host2;
        } else {
            System.out.println("something wrong4, base: "+base+", new_uri: "+s);
            return "";
        }
        if (port1 != null) {
            ret += ":" + port1;
        } else {
            ret += ret.contains("https") ? ":443" : ":80";
        }
        if (detail1 == null) {
            System.out.println("something wrong5, base: "+base+", new_uri: "+s);
            return base;
        } else {
            if (detail1.startsWith("/")) {
                ret += detail1;
            } else {
                String[] tmp = (detail2.substring(0, detail2.lastIndexOf("/")) + "/" + detail1).split("/");
                LinkedList<String> url_parts = new LinkedList<String>();
                for (int i=0; i<tmp.length; i++) {
                    if (tmp[i].equals("")) {
                        continue;
                    }
                    if (!tmp[i].equals("..")) {
                        url_parts.add(tmp[i]);
                    } else {
                        if (url_parts.size() == 0) {
                            return "";
                        }
                        url_parts.removeLast();
                    }
                }
                ret += "/" + String.join("/", url_parts);
            }
        }
        return ret;
    }

    public static Boolean isValidLink(String s){
        // Exclude certain file types and special links
        if (s.startsWith("//")){
            return false;
        }

        if ((!s.contains("en") && !s.contains("wikipedia.org")) || !s.startsWith("/")){
            return false;
        }

        if (s.isEmpty() || s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".gif") || s.endsWith(".png") ||
                s.endsWith(".txt") || s.endsWith(".svg") || s.contains("Special:") || s.contains("File:") || s.contains("index.php") ||
                s.contains("Wikipedia:") || s.contains("identifier") || s.contains("Template:") || s.contains("Template_talk:") || s.contains("Help:") || s.contains("Category:") || s.contains("Talk:")) {
            return false;
        }

        return true;
    }
}
