package cis5550.jobs;

import cis5550.flame.FlamePair;
import cis5550.flame.FlamePairRDD;
import cis5550.flame.FlameRDD;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Hasher;
import cis5550.tools.URLParser;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PageRank {
    static final String tableName = "pt-crawl", rankTableName = "pt-pageranks", delimiter = ",";
    static final double decayFactor = 0.85;
    static Set<String> visited = new HashSet<>();
    public static void run(cis5550.flame.FlameContext ctx, String[] args) throws Exception {
        double convergence = 0.1;
        int totalProcessed = 0;
        AtomicInteger converged = new AtomicInteger();
        double cutoff = -1;

        if (args.length > 0) {
            try {
                convergence = Double.parseDouble(args[0]);
                if (args.length == 2) cutoff = Double.parseDouble(args[1]);
            } catch (Exception ignored) {
            }
        }
        FlameRDD rdd = ctx.fromTable(tableName, row -> row.get("url") + "@" + row.get("children_url"));
        FlamePairRDD state = rdd.mapToPair(s -> {
            int idx = s.indexOf('@');
            String url = s.substring(0, idx);
            String page = s.substring(idx + 1);
            String[] links = page.split("\\s+");//processUrls(getAllUrls(page), url);
            List<String> hashedLinks = new ArrayList<>();
            System.out.println("processing links size; "+links.length);
            for (String link: links) {
                System.out.println("individual link: "+link);
                if (visited.contains(link)) {
			System.out.println("already ranked the link: "+link);
			continue;
		}
		visited.add(link);
		hashedLinks.add(Hasher.hash(link));
            }
            return new FlamePair(Hasher.hash(url), "1.0,1.0," + String.join(delimiter, hashedLinks));
        });
        while (true) {
            // Compute and aggregate transfer table
            FlamePairRDD transfer = state.flatMapToPair(p -> {
                List<FlamePair> pairs = new ArrayList<>();
                String[] values = p._2().split(delimiter);
                double cRank = Double.parseDouble(values[0].strip());
                double n =values.length - 2;
                for (int i = 2; i < values.length; i++) {
                    pairs.add(new FlamePair(values[i], String.valueOf(cRank * decayFactor / n)));
                }
                // You may want to additionally send rank 0.0 from each vertex to itself,
                // to prevent vertexes with indegree zero from disappearing during the join later on.
                pairs.add(new FlamePair(p._1(), "0.0"));
//                System.out.println(Arrays.toString(pairs.toArray()));
                System.out.println("state RDD flats map to pairs size: "+pairs.size());
                return pairs;
            }).foldByKey("0", (a, b) -> "" + (Double.parseDouble(a) + Double.parseDouble(b)));

            // Join state and transfer tables
            state = state.join(transfer).flatMapToPair(p -> {
                List<FlamePair> pairs = new ArrayList<>();
                String[] values = p._2().split(delimiter);
                int len = values.length;
                // This is also a good opportunity to add the 0.15 from the rank source.
                values[1] = values[0];
                values[0] = String.valueOf(Double.parseDouble(values[len - 1]) + 0.15);
                values = Arrays.copyOf(values, values.length-1);
                pairs.add(new FlamePair(p._1(), String.join(delimiter, values)));
//                System.out.println(Arrays.toString(pairs.toArray()));
                System.out.println("joining tables to flat map to pairs size: "+pairs.size());
                return pairs;
            });

            totalProcessed++;

            // calculate the convergence
            double finalConvergence = convergence;
            String max = state.flatMap(p -> {
                String[] values = p._2().split(delimiter);
                double diff = Math.abs(Double.parseDouble(values[0]) - Double.parseDouble(values[1]));
                if (diff < finalConvergence) converged.getAndIncrement();
                return List.of(new String[]{String.valueOf(diff)});
            }).fold("0", (s1, s2) -> Double.parseDouble(s1) >= Double.parseDouble(s2)? s1 : s2);

            // if meet the requirement, quit the loop
            System.out.println("current max is "+max);
            if (Double.parseDouble(max) < convergence) break;
            else if (cutoff >= 0 && (double) (converged.get() / totalProcessed) * 100 >= cutoff) break;
        }


        state.flatMapToPair(p -> {
            KVSClient client = ctx.getKVS();
            Row r = new Row(p._1());
            r.put("rank", p._2().strip().split(delimiter)[0]);
            client.putRow(rankTableName, r);
            return List.of();
        });
    }

    public static List<String> processUrls(List<String> urls, String host) {
        List<String> processed = new ArrayList<>();

        String[] hosts = URLParser.parseURL(host);
        String protocol = hosts[0].toLowerCase(), hostName = hosts[1].toLowerCase(), port = hosts[2], subDomains = hosts[3];
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
}


/*Yuhao's page rank implementation is as following*/

//package cis5550.jobs;
//import java.io.IOException;
//import java.util.*;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//import cis5550.flame.FlameContext;
//import cis5550.flame.FlamePair;
//import cis5550.flame.FlamePairRDD;
//import cis5550.kvs.KVSClient;
//import cis5550.tools.Hasher;
//import cis5550.tools.URLParser;
//
//import static java.lang.Double.parseDouble;
//
//public class PageRank {
//    public static void run(FlameContext ctx, String[] args) {
//        // Set the convergence threshold to 0.1 if not provided (according to the pdf file)
//        double threshold = args.length > 0 ? parseDouble(args[0]) : 0.1;
//
//        try {
//            // Load pages from table and map to pairs of URLs and their corresponding page contents
//            FlamePairRDD pages = ctx.fromTable("pt-crawl", record -> record.get("url") + "," + record.get("children_url"))
//                    .mapToPair(PageRank::extractLinks);
//
//            while (true) {
//                // Calculate contributions of each page to the rank of other pages
//                FlamePairRDD pageRankContributions = pages.flatMapToPair(PageRank::calculateOutgoingContributions)
//                        .foldByKey("0", PageRank::sumRankContributions);
//
//                // Update the PageRank for each page based on contributions
//                pages = pages.join(pageRankContributions).flatMapToPair(PageRank::updatePageRankContributions);
//
//                // Calculate the maximum difference in rank values to check for convergence
//                String diff = pages.flatMap(PageRank::calculateRankDifferences).fold("0", PageRank::calculateMaxDifference);
//
//                // If the maximum difference is less than the threshold, break the loop
//                if (Double.parseDouble(diff) < threshold) {
//                    break;
//                }
//            }
//
//            // Store the final PageRank values in a key-value store
//            pages.flatMapToPair(page -> updatePageRanks(ctx, page));
//        } catch (Exception e) {
//            System.out.println(e);
//        }
//    }
//    private static String normalizeURL(String url, String base) {
//        // Remove anchor tags from the URL
//        int hashIndex = url.indexOf("#");
//        if (hashIndex != -1) {
//            url = url.substring(0, hashIndex);
//        }
//
//        // Return base URL if the URL is empty or points to a resource file
//        if (url.isEmpty() || url.matches(".*\\.(jpg|jpeg|gif|png|txt)$")) {
//            return base;
//        }
//
//        // Parse the URLs
//        String[] parsedUrl = URLParser.parseURL(url);
//        String[] parsedBase = URLParser.parseURL(base);
//
//        // Extract the components of both URLs
//        String protocolUrl = parsedUrl[0];
//        String hostUrl = parsedUrl[1];
//        String portUrl = parsedUrl[2];
//        String pathUrl = parsedUrl[3];
//
//        String protocolBase = parsedBase[0];
//        String hostBase = parsedBase[1];
//        String portBase = parsedBase[2];
//        String basePath = parsedBase[3];
//
//        // Normalize the protocol
//        String protocol = protocolUrl != null ? protocolUrl : protocolBase;
//        if (protocol == null || (!protocol.equals("http") && !protocol.equals("https"))) {
//            System.out.println("Invalid protocol, base: " + base + ", new_uri: " + url);
//            return "";
//        }
//
//        // Normalize the host
//        String host = hostUrl != null ? hostUrl : hostBase;
//        if (host == null) {
//            System.out.println("Invalid host, base: " + base + ", new_uri: " + url);
//            return "";
//        }
//
//        // Normalize the port
//        String port = portUrl != null ? portUrl : (portBase != null ? portBase : (protocol.equals("https") ? "443" : "80"));
//
//        // Build the normalized URL
//        StringBuilder normalizedUrl = new StringBuilder(protocol + "://" + host + ":" + port);
//
//        // Normalize the path
//        if (pathUrl != null) {
//            if (!pathUrl.startsWith("/")) {
//                pathUrl = resolveRelativePath(basePath, pathUrl);
//            }
//            normalizedUrl.append(pathUrl);
//        } else {
//            normalizedUrl.append(basePath);
//        }
//
//        return normalizedUrl.toString();
//    }
//
//    private static String resolveRelativePath(String basePath, String relativePath) {
//        LinkedList<String> urlParts = new LinkedList<>();
//        String[] baseParts = basePath.substring(0, basePath.lastIndexOf("/")).split("/");
//        String[] relativeParts = relativePath.split("/");
//
//        for (String part : relativeParts) {
//            if (part.equals("..") && !urlParts.isEmpty()) {
//                urlParts.removeLast();
//            } else if (!part.isEmpty() && !part.equals(".") && !part.equals("..")) {
//                urlParts.add(part);
//            }
//        }
//
//        return "/" + String.join("/", urlParts);
//    }
//    public static List<String> findUrls(String content) {
//        String regex = "<a\\s+(?:[^>]*?\\s+)?href=['\"]([^'\"]*)['\"]";
//        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
//        List<String> urls = new ArrayList<>();
//        Matcher matcher = pattern.matcher(content);
//
//        while (matcher.find()) {
//            urls.add(matcher.group(1));
//        }
//
//        return urls;
//    }
//
//    private static FlamePair extractLinks(String entry){
//        System.out.println("source string for links:\n"+entry);
//        int commaIndex = entry.indexOf(",");
//        String page = entry.substring(commaIndex + 1);
////        StringBuilder links = new StringBuilder();
//        String base = entry.substring(0, commaIndex);
////
////        for (String line : page.split("\n")) {
////            List<String> newRawUrls = findUrls(line);
////            for (String newRawUrl : newRawUrls) {
////                String newUrl = normalizeURL(newRawUrl, base);
////                if (!newRawUrl.isEmpty() && !newUrl.isEmpty()) {
////                    if (!links.isEmpty()) {
////                        links.append(",");
////                    }
////                    links.append(newUrl);
////                }
////            }
////        }
//        return new FlamePair(base, "1.0,1.0," + String.join(",", page.split("\\s+")));
//    }
//
//    private static LinkedList<FlamePair> calculateOutgoingContributions(FlamePair page){
//        LinkedList<FlamePair> contributionsList = new LinkedList<>();
//        String currentPageUrl = page._1();
//        String[] rankAndLinks = page._2().split(",");
//        double currentPageRank = Double.parseDouble(rankAndLinks[0]);
//        Set<String> outgoingLinks = new HashSet<>(Arrays.asList(Arrays.copyOfRange(rankAndLinks, 2, rankAndLinks.length)));
//
//        for (String linkUrl : outgoingLinks) {
//            double linkContribution = 0.85 * currentPageRank / outgoingLinks.size();
//            contributionsList.add(new FlamePair(linkUrl, Double.toString(linkContribution)));
//        }
//
//        if (!outgoingLinks.contains(currentPageUrl)) {
//            contributionsList.add(new FlamePair(currentPageUrl, "0"));
//        }
//
//        return contributionsList;
//    }
//
//    private static List<FlamePair> updatePageRankContributions(FlamePair page){
//        List<FlamePair> updatedPageRanks = new LinkedList<>();
//        String pageUrl = page._1();
//        String[] rankValues = page._2().split(",");
//
//        String updatedRank = String.valueOf(0.15 + Double.parseDouble(rankValues[rankValues.length - 1]));
//        String originalRank = rankValues[0];
//        String[] outgoingLinks = Arrays.copyOfRange(rankValues, 2, rankValues.length - 1);
//
//        String pageRankRecord = updatedRank + "," + originalRank + "," + String.join(",", outgoingLinks);
//        updatedPageRanks.add(new FlamePair(pageUrl, pageRankRecord));
//
//        return updatedPageRanks;
//    }
//
//    private static List<String> calculateRankDifferences(FlamePair page){
//        List<String> rankDifferences = new ArrayList<>();
//        String[] ranks = page._2().split(",");
//        Double currentRank = Double.parseDouble(ranks[0]);
//        Double prevRank = Double.parseDouble(ranks[1]);
//        rankDifferences.add(String.valueOf(Math.abs(currentRank - prevRank)));
//        return rankDifferences;
//    }
//
//    private static List<FlamePair> updatePageRanks(FlameContext ctx, FlamePair page) throws IOException {
//        List<FlamePair> updates = new ArrayList<>();
//        KVSClient kvs = ctx.getKVS();
//        String hashedUrl = Hasher.hash(page._1());
//        String rankValue = page._2().split(",")[0];
//        kvs.put("pt-pageranks", hashedUrl, "rank", rankValue);
//        return updates;
//    }
//    private static String sumRankContributions(String rank1, String rank2) {
//        double combinedRank = Double.parseDouble(rank1) + Double.parseDouble(rank2);
//        return Double.toString(combinedRank);
//    }
//
//    private static String calculateMaxDifference(String diff1, String diff2) {
//        return String.valueOf(Math.max(Double.parseDouble(diff1), Double.parseDouble(diff2)));
//    }
//}
