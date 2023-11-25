package cis5550.jobs;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import cis5550.tools.*;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import cis5550.flame.FlameContext;
import cis5550.flame.FlameRDD;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.commons.logging.LogFactory;

public class Crawler {
    private static final Pattern LINK_PATTERN = Pattern.compile("<a\\s+(?:[^>]*?\\s+)?href=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Set<String> seenUrls = ConcurrentHashMap.newKeySet();
    private static final int MAX_PERMITS = 10; // You can adjust this value as needed
    private static final Set<String> globalUrlSet = ConcurrentHashMap.newKeySet();
    private static final int TIMEOUT = 5000;
    private static final String USER_AGENT = "cis5550-crawler";
    private static final int MAX_URLS_TO_EXTRACT = 100;
    private static final HashSet<Integer> s = new HashSet<Integer>(Arrays.asList(301, 302, 303, 307, 308));

    private static final Logger logger = Logger.getLogger(Crawler.class);
    public static void run(FlameContext ctx, String[] arr) {

        String kvs_master = ctx.getKVS().getCoordinator();

        List<String> seed = new LinkedList<String>();
        Boolean cache = Boolean.valueOf(arr[1]);

        if (arr.length >= 2 && arr[0].equals("seed")) {
            for (int i=2; i<arr.length; i++) {
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

                int finalCount = count;
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
                            System.out.println("Test 1");
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
                            System.out.println("Test 2");
                            host_row = new Row(parsed_url[1]);
                            Connection.Response robot_con = Jsoup.connect(parsed_url[0] + "://" + parsed_url[1] + "/robots.txt")
                                    .userAgent(USER_AGENT)
                                    .timeout(TIMEOUT)
                                    .ignoreContentType(true) // Important for fetching plain text files
                                    .execute();

                            System.out.println("Test 5");

                            if (robot_con.statusCode()  == 200) {
                                String robotsTxt = robot_con.body();
                                host_row.put("robots_txt", robotsTxt);
                            }
                            System.out.println("Test 3");
                        }
                        System.out.println("Test 4");

                        host_row.put("last_accessed_time", String.valueOf(System.currentTimeMillis()));
                        kvs.putRow("hosts", host_row);
                        Connection.Response response = Jsoup.connect(u)
                                .userAgent(USER_AGENT)
                                .timeout(TIMEOUT)
                                .followRedirects(false) // important if you want to handle redirects manually
                                .method(Connection.Method.GET)
                                .ignoreContentType(true) // to fetch non-HTML content types
                                .execute();


                        Row row = new Row(hashKey);
                        row.put("url", u);


                        int code = response.statusCode();
                        row.put("responseCode", String.valueOf(code));


                        // Access response headers directly from the Response object
                        String contentEncoding = response.header("Content-Encoding");
                        String contentLanguage = response.header("Content-Language");
                        String contentType = response.contentType(); // Convenience method for Content-Type header
                        String contentLength = response.header("Content-Length");

//                        if (contentEncoding != null && "gzip".equalsIgnoreCase(contentEncoding)) {
//                            return new_url_list;
//                        }
//                        if (contentEncoding != null) {
//                            System.out.println("url Encoding ");
//                            return new_url_list;
//                        }
                        if (contentLanguage != null && !contentLanguage.startsWith("en")) {
                            return new_url_list;
                        }
                        if (contentType != null) {
                            row.put("contentType", contentType);
                        }
                        if (contentLength != null) {
                            row.put("length", contentLength);
                        }

                        System.out.println("Content Type: " + contentType);
                        System.out.println("Content Length: " + contentLength);
                        System.out.println("Content Language: " + contentLanguage);

                        if (code == 200 && contentType != null && contentType.startsWith("text/html")) {
                            // Check the status code and content type

                            Document doc = response.parse();
                            String bodyText = doc.text();
                            String limitedBodyText = bodyText.substring(0, Math.min(bodyText.length(), 10000));

                            // When saving the page content
                            try {
                                savePageContent(u, doc.outerHtml(), row);
                            } catch (IOException e) {
                                e.printStackTrace(); // Or handle the exception as appropriate
                            }
                            row.put("page", limitedBodyText);

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

                        if (code == 200 && contentType != null && contentType.equals("application/pdf")){
                            try (InputStream pdfStream = response.bodyStream()) {
                                String pdfText = extractTextFromPDF(pdfStream);

                                kvs.put("pt-pdf", hashKey, "page", pdfText);
                            }
                        }

                        kvs.putRow("pt-crawl", row);
                        kvs.put("crawled-url", hashKey, "url", u);
//                        System.gc();
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
//        if (s.startsWith("//")){
//            return false;
//        }


        if ((s.contains("en") && s.contains("wikipedia.org")) || s.contains("www.bbc.com") || s.contains("edition.cnn") || s.startsWith("/")) {
        } else {
            return false;
        }

        if (s.isEmpty() || s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".gif") || s.endsWith(".png") ||
                s.endsWith(".txt") || s.endsWith(".svg") || s.contains("Special:") || s.contains("File:") || s.contains("index.php") || s.contains("mailto") || s.contains("download") || s.contains("cloud.email") || s.contains("contact-us") || s.contains("localhost") || s.contains("usingthebbc") ||
                s.contains("Wikipedia:") || s.contains("identifier") || s.contains("Template:") || s.contains("Template_talk:") || s.contains("Help:") || s.contains("Category:") || s.contains("Talk:") || s.contains("Portal:")) {
            return false;
        }

        return true;
    }

    private static void savePageContent(String url, String htmlContent, Row row) throws IOException {
        // Create a unique identifier for the content, e.g., a hash of the URL

        String hash = Hasher.hash(url);

        Path cacheDirectoryPath = Paths.get("cache_pages");

        // Ensure the 'cache_pages' directory exists
        if (!Files.exists(cacheDirectoryPath)) {
            Files.createDirectories(cacheDirectoryPath);
        }

        // Resolve the path to the file within the 'cache_pages' directory
        Path filePath = cacheDirectoryPath.resolve(hash + ".html.gz");

        // Check if the file already exists
        if (Files.exists(filePath)) {
            // Log this situation if needed
            System.out.println("File already exists for URL: " + url);
            return; // Skip saving as the file already exists
        }

        try (OutputStream fileOutputStream = Files.newOutputStream(filePath);
             BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(bufferedOutputStream)) {

            // Write the compressed HTML content to the file
            gzipOutputStream.write(htmlContent.getBytes(StandardCharsets.UTF_8));
        }

        // Assuming KVSClient is your abstraction for the key-value storage
        row.put("cache_path", filePath.toString());
    }

    private static String extractTextFromPDF(InputStream pdfStream) throws IOException {
        try (PDDocument document = PDDocument.load(pdfStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
}
