package cis5550.jobs;

import cis5550.flame.*;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Helpers;
import cis5550.tools.HttpCrawlResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Crawler {

    public static FlameRDD urlQueue = null;

    /**
     * Check whether the latter contains a single element (the seed URL),
     * and output an error message (using the context’s output method)
     * if it does not.
     */
    public static boolean isValidArgs(FlameContext context, String[] args) {
        // If we have exactly one, return true
        if (args.length == 1) return true;
        // Else, print error message and exit
        context.output("Invalid number of arguments, must be exactly one: the seed url.");
        return false;
    }

    public static void initializeRobotsPolicy(KVSClient kvsClient, String url) throws IOException {
        // Split url, get robots.txt
        String robotsUrl = Helpers.mergeLinks(url, "/robots.txt");
        String robotsHost = Helpers.getHost(robotsUrl);

        // Make http call
        HttpCrawlResponse httpRobots = Helpers.httpRequest(robotsUrl, "GET");

        String robotsFileContents = "";

        // If there was a robots file
        if(httpRobots.httpCode == 200) {
            // Get data
            robotsFileContents = httpRobots.content;
        }

        kvsClient.put("hosts", robotsHost, "robotsPolicy", robotsFileContents);
    }

    public static HashMap<String, String> loadRobotsPolicy(KVSClient kvsClient, String hostItem) throws IOException {
        String robotsPolicyText = new String(kvsClient.get("hosts", hostItem, "robotsPolicy"), StandardCharsets.UTF_8);;
        return Helpers.parseRobotsFile(robotsPolicyText);
    }

    public static HashMap<String, String> loadRulesSet(KVSClient kvsClient, String hostItem) throws IOException {
        HashMap<String, String> robotsPolicy = loadRobotsPolicy(kvsClient,  hostItem);
        HashMap<String, String> ruleSet = new HashMap<>();
        boolean matchedHost = false;

        // Iterating HashMap through for loop
        for (Map.Entry<String, String> set : robotsPolicy.entrySet()) {
            // Printing all elements of a Map
            if(set.getKey().equalsIgnoreCase("user-agent")) {
                if(set.getValue().equals("*") || set.getValue().equalsIgnoreCase(hostItem)) {
                    matchedHost = true;
                } else {
                    matchedHost = false;
                }
            }

            if(matchedHost) {
                String key = set.getKey();
                String value = set.getValue();

                // Check if the key is 'allowed' or 'disallowed'
                if(key.equals("allow") || key.equals("disallow")) {
                    // If so, set key: route, value: allowed, disallowed
                    ruleSet.put(value, key.toLowerCase());
                }

                // Check if we have a 'crawl-delay' key
                if(key.equals("crawl-delay")) {
                    // key: crawl-delay, value: number as string
                    ruleSet.put("crawl-delay", value);
                }
            }
        }
        return ruleSet;
    }

    public static boolean isDisallowed(HashMap<String, String> rulesSet, String url) {
        // First, parse and get the domain's route path
        String path = Helpers.getPath(url);

        // Iterating HashMap through for loop
        for (Map.Entry<String, String> set : rulesSet.entrySet()) {
            String key = set.getKey().toLowerCase();
            String value = set.getValue().toLowerCase();

            if(!key.equalsIgnoreCase("crawl-delay")) {
                // Printing all elements of a Map
                if(path.toLowerCase().startsWith(key.toLowerCase())) {
                    // Check if the rule is to disallow
                    if(value.equals("disallow")) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static int getWait(HashMap<String, String> rulesSet) {
        try {
            float floatValue = Float.parseFloat(rulesSet.get("crawl-delay"));
            if(floatValue < 1 && floatValue > 0) floatValue = 1;
            return (int)(floatValue * 1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static boolean performRateLimit(HashMap<String, String> rulesSet) {
        return rulesSet.containsKey("crawl-delay");
    }

    public static long lastAccessTimestamp(KVSClient kvsClient, String hostItem) {
        try {
            String lastAccessString = new String(kvsClient.get("hosts", hostItem, "lastAccess"), StandardCharsets.UTF_8);;
            return Long.parseLong(lastAccessString);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static void updateLastAccess(KVSClient kvsClient, String hostItem) throws IOException {
        long currentTime = System.currentTimeMillis();

        kvsClient.put("hosts", hostItem, "lastAccess", Long.toString(currentTime));
    }

    public static void checkExtra(KVSClient kvsClient, String domainItem) throws IOException {
        String authority = Helpers.getRootUrl(domainItem);
        String authorityHash = Helpers.hashUrl(authority);

        if(!kvsClient.existsRow(
            "pt-crawl",
            authorityHash
        )) {
            Row row = new Row(authorityHash);
            row.put("responseCode", Integer.toString(200));
            row.put("contentType", "text/html");
            row.put("length", Integer.toString(0));
            row.put("url", authority);
            kvsClient.putRow("pt-crawl", row);
        }
    }

    public static void run(FlameContext context, String[] args) throws Exception {
        // Exit if there are missing arguments
        if(!isValidArgs(context, args)) return;

        String seedUrl = args[0];
        context.output("Seed url: " + seedUrl + "\n");

        /**
         * Create an initial FlameRDD (perhaps called urlQueue)
         * by parallelizing the seed URL and then set up a while
         * loop that runs until urlQueue.count() is zero.
         */

        List<String> urlList = new ArrayList<>();
        String normalizedUrl = Helpers.normalizeUrl(seedUrl);
        urlList.add(normalizedUrl);
        urlQueue = context.parallelize(urlList);
        int recordsProcessed = 0;

        // Get KVS Coordinator
        String kvsCoordinator = context.getKVS().getCoordinator();

        KVSClient kvsClientLocal = new KVSClient(kvsCoordinator);
        checkExtra(kvsClientLocal, normalizedUrl);


        while (urlQueue.count() > 0) {
            recordsProcessed++;
            context.output("Current size of urlQueue: " + urlQueue.count() + "\n");

            urlQueue = urlQueue.flatMap( domainItem -> {
                ArrayList<String> outputList = new ArrayList<>();
                // Access the coordinator
                KVSClient kvsClient = new KVSClient(kvsCoordinator);
                // Generate the domain hash
                String domainItemHash = Helpers.hashUrl(domainItem);

                // Now we need the domain host, for robots policy
                String hostItem = Helpers.getHost(domainItem);

                if(Helpers.containsIgnoredExtension(domainItem)) {
                    return outputList;
                }


                // Add redirect codes
                ArrayList<Integer> redirectCodes = new ArrayList<>(Arrays.asList(301, 302, 303, 307, 308));

                // Now generate the hash code for this domain
                Row row = new Row(domainItemHash);

                // Check if there is NOT an entry for robots for this host
                if(!kvsClient.existsRow(
                    "hosts",
                    hostItem
                )) {
                    initializeRobotsPolicy(
                        kvsClient,
                        domainItem
                    );
                }

                // Load the rules set
                HashMap<String, String> rulesSet = loadRulesSet(kvsClient, hostItem);

                // If this crawl is disallowed
                if(isDisallowed(rulesSet, domainItem)) {
                    // Exit this crawl
                    return outputList;
                }


                if(performRateLimit(rulesSet)) {
                    int waitInterval = getWait(rulesSet); // Gets milliseconds
                    long lastAccessed = lastAccessTimestamp(kvsClient, hostItem);
                    long currentTime = System.currentTimeMillis();
                    // There is no update
                    if(lastAccessed > 0) {
                        // There is a rule, we must check
                        if ((currentTime - lastAccessed) < waitInterval) {
                            // Add it back to the queue, and wait for next iteration
                            outputList.add(hostItem);
                            return outputList;
                        }
                    }
                }

                updateLastAccess(kvsClient, hostItem);

                // Check if domainItem has already been crawled
                if (
                    kvsClient.existsRow(
                        "pt-crawl",
                        domainItemHash
                    )
                ) {
                    return outputList;
                } else {
                    /**
                     * HEAD
                     */
                    HttpCrawlResponse htmlRespHead = Helpers.httpRequest(domainItem, "HEAD");
//                    System.out.println("HEAD: " + domainItem + ", response: " + htmlRespHead);

                    // Check if this is a redirect
                    if(htmlRespHead.httpCode != 200) {

                        // Check if it is a redirect
                        if(redirectCodes.contains(htmlRespHead.httpCode)) {
                            String redirectLocation = Helpers.mergeLinks(
                                domainItem,
                                Helpers.extractHeader(htmlRespHead.headers, "Location")
                            );
                            // Add the new url for redirection
                            outputList.add(redirectLocation);

                        }

                        // Add to visited list for HEAD
                        row.put("responseCode", Integer.toString(htmlRespHead.httpCode));
                        row.put("contentType", Helpers.extractHeader(htmlRespHead.headers, "Content-Type"));
                        row.put("length", Helpers.extractHeader(htmlRespHead.headers, "Content-Length"));
                        row.put("url", domainItem);

                        kvsClient.putRow("pt-crawl", row);
                        return outputList;
                    }
                    // HEAD RESPONDED WITH 200
                    else {
                        /**
                         * GET
                         * - If the response to the HEAD was a 200 and the content type
                         * was text/html, there should also be a page column that contains
                         * the body of the GET response exactly as it was returned by the
                         * server (same sequence of bytes).
                         */


                        HttpCrawlResponse htmlRespGet = Helpers.httpRequest(domainItem, "GET");
//                        System.out.println("GET: " + domainItem + ", response: " + htmlRespHead);

                        String contentType = Helpers.extractHeader(htmlRespGet.headers, "Content-Type");
                        String contentLength = Helpers.extractHeader(htmlRespGet.headers, "Content-Length");

                        boolean isPage = htmlRespHead.httpCode == 200 && Objects.equals(contentType, "text/html");

                        // This is a regular GET extraction
                        if(htmlRespHead.httpCode == 200) {
                            String[] extractedLinks = Helpers.extractLinks(
                                htmlRespGet.content,
                                domainItem
                            );

                            for(String link : extractedLinks) {
                                outputList.add(link);
                            }
                        }

                        // Add to visited list for GET
                        row.put("responseCode", Integer.toString(htmlRespGet.httpCode));
                        row.put("contentType", contentType);
                        row.put("length", contentLength);
                        row.put("url", domainItem);

                        if(isPage) {
                            row.put("page", htmlRespGet.data);
                        }
                        kvsClient.putRow("pt-crawl", row);
                    }
                }

                return outputList;
            });
            Thread.sleep(1000);
        }

        // Print all processed records
        context.output("Total records processed: " + recordsProcessed + "\n");
    }
}
