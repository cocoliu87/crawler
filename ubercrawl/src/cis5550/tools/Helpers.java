package cis5550.tools;

import cis5550.external.PorterStemmer;
import cis5550.kvs.KVSClient;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Helpers {

    public static final HashSet<String> stopWords = new HashSet<>(List.of("a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "arent", "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by", "cant", "cannot", "could", "couldnt", "did", "didnt", "do", "does", "doesnt", "doing", "dont", "down", "during", "each", "few", "for", "from", "further", "had", "hadnt", "has", "hasnt", "have", "havent", "having", "he", "hed", "hell", "hes", "her", "here", "heres", "hers", "herself", "him", "himself", "his", "how", "hows", "i", "id", "ill", "im", "ive", "if", "in", "into", "is", "isnt", "it", "its", "its", "itself", "lets", "me", "more", "most", "mustnt", "my", "myself", "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves", "out", "over", "own", "same", "shant", "she", "shed", "shell", "shes", "should", "shouldnt", "so", "some", "such", "than", "that", "thats", "the", "their", "theirs", "them", "themselves", "then", "there", "theres", "these", "they", "theyd", "theyll", "theyre", "theyve", "this", "those", "through", "to", "too", "under", "until", "up", "very", "was", "wasnt", "we", "wed", "well", "were", "weve", "were", "werent", "what", "whats", "when", "whens", "where", "wheres", "which", "while", "who", "whos", "whom", "why", "whys", "with", "wont", "would", "wouldnt", "you", "youd", "youll", "youre", "youve", "your", "yours", "yourself", "yourselves"));

    public static String removeQuotes(String inputHtml) {
        return inputHtml
            .replaceAll("\"", "")
            .replaceAll("'", "");
    }

    public static String[] extractLinks(String sourceHtml, String originUrl) {

        ArrayList<String> hrefLinks = new ArrayList<>();

        String HTML_TAG_PATTERN = "(?i)<a([^>]+)>(.+?)</a>";
        String HTML_HREF_TAG_PATTERN = "\\s*(?i)href\\s*=\\s*(\"([^\"]*\")|'[^']*'|([^'\">\\s]+))";

        Pattern pTag = Pattern.compile(HTML_TAG_PATTERN);
        Pattern pLink = Pattern.compile(HTML_HREF_TAG_PATTERN);
        Matcher mTag = pTag.matcher(sourceHtml);

        while (mTag.find()) {

            String href = mTag.group(1);     // get the values of href
            Matcher mLink = pLink.matcher(href);

            while (mLink.find()) {
                String hrefLink = removeQuotes(mLink.group(1));
                String finalUrl = mergeLinks(originUrl, hrefLink);
//
//                System.out.println("originUrl: " + originUrl);
//                System.out.println("hrefLink: " + hrefLink);
//                System.out.println("finalUrl: " + finalUrl + "\n");
                hrefLinks.add(finalUrl);
            }

        }

        return hrefLinks.toArray(new String[0]);
    }

    public static String truncateUrlTag(String inputUrl) {
        return inputUrl.contains("#")
            ? inputUrl.substring(0, inputUrl.indexOf("#"))
            : inputUrl;
    }

    public static String truncateQuestionMark(String inputUrl) {
        return inputUrl.contains("?")
                ? inputUrl.substring(0, inputUrl.indexOf("?"))
                : inputUrl;
    }

    public static boolean containsIgnoredExtension(String inputUrl) {
        String[] ignoredExtensions = {
            ".png", ".jpg", ".gif", ".png", ".mp3", ".mp4"
        };

        for(String ignoredExt : ignoredExtensions) {
            if(inputUrl.endsWith(ignoredExt)) {
                return true;
            }
        }

        return false;
    }

    public static boolean allowedContentType(String contentType) {
        String[] allowedContentType = {
            "text/plain", "text/html",
        };

        for(String currentContentType : allowedContentType) {
            if(contentType.equals(currentContentType)) {
                return true;
            }
        }

        return false;
    }

    public static String getPath(String url) {
        try {
            URL parsedUrl = new URL(url);
            return parsedUrl.getPath();
        } catch (Exception e) {
            return "";
        }
    }

    public static String getHost(String url) {
        try {
            URL parsedUrl = new URL(url);
            return parsedUrl.getHost();
        } catch (Exception e) {
            return "";
        }
    }



    public static String getRootUrl(String url) {
        try {
            URL parsedUrl = new URL(url);
            return parsedUrl.getProtocol() + "://" + parsedUrl.getAuthority();
        } catch (Exception e) {
            return "";
        }
    }

    public static String getRelativePath(String url) {
        try {
            URL parsedUrl = new URL(url);
            String path  = parsedUrl.getPath();
            if(path.length() > 0) {
                path = path.substring(0, path.lastIndexOf("/"));
                if(path == "/") path = "";
            }
            return parsedUrl.getProtocol() + "://" + parsedUrl.getAuthority() + path;
        } catch (Exception e) {
            return "";
        }
    }

    public static String mergeLinks(String originUrl, String hrefLink) {
        String prefix;
        String link = hrefLink;

        if(originUrl == null) return hrefLink;

        // If href link is a tag, just return the origin url
        if(Objects.equals(hrefLink, "#")) return originUrl;

        // Check if hrefLink starts with http or https
        if(hrefLink.startsWith("http://") || hrefLink.startsWith("https://")) {
            // If the root url is not the same, then href link takes priority
            return hrefLink;
        }

        // Href is from the root path
        if(hrefLink.startsWith("/")) {
            // Then prefix must be the root domain
            prefix = getRootUrl(originUrl);
        } else {
            // It must be relative
            prefix = getRelativePath(originUrl);
        }


        // Avoid double slashes
        if(prefix.endsWith("/") && hrefLink.startsWith("/"))
            link = link.substring(1);

        // Avoid no slashes
        if(!originUrl.endsWith("/") && !hrefLink.startsWith("/"))
            link = "/" + link;

//        System.out.println("prefix: " + prefix);
//        System.out.println("link: " + link);

        return prefix + link;
    }

    public static String normalizeHost(String url) {
        try {
            URL parsedUrl = new URL(url);
            String port = Objects.equals(parsedUrl.getProtocol(), "http") ? "80" : "443";
            return parsedUrl.getProtocol() + "://"
                    + parsedUrl.getHost() + ":" + port +  parsedUrl.getFile();
        } catch (Exception e) {
            return "";
        }
    }

    public static String normalizeUrl(String inputUrl) {
        // Check if there are url tags that need removing
        inputUrl = truncateUrlTag(inputUrl);
        inputUrl = truncateQuestionMark(inputUrl);
        inputUrl = normalizeHost(inputUrl);
        return inputUrl;
    }

    /**
     * Splits a line into a key and a value
     * @param line
     * @return
     */
    public static String[] parseRobotsLine(String line) {
        if(!line.contains(":")) return null;
        String[] tokens = line.split(":");
        String key = tokens[0].trim();
        String value = tokens[1].trim();

        return new String[] {
            key,
            value
        };
    }

    /**
     * Splits multiple lines into individual lines, then parses
     * each line to generate a map of key-values
     * @param contents
     * @return
     */
    public static HashMap<String, String> parseRobotsFile(String contents) {
        HashMap<String, String> values = new HashMap<>();

        String[] lines = contents.split("\n");

        for(String line : lines) {
            String[] kvLine = parseRobotsLine(line);
            if(kvLine != null) values.put(kvLine[0], kvLine[1]);
        }

        return values;
    }

    public static String getStreamBody(InputStream inputStream) throws IOException {
        StringBuilder content = new StringBuilder();
        byte[] data = inputStream.readAllBytes();
        return new String(data, StandardCharsets.UTF_8);
    }

    public static byte[] getStreamBodyBytes(InputStream inputStream) throws IOException {
        StringBuilder content = new StringBuilder();
        return inputStream.readAllBytes();
    }

    public static HttpCrawlResponse httpRequest(String requestUrl, String method) throws IOException {
        // Parse the url
        URL url = new URL(requestUrl);
        // Determine method to use
        String finalMethod = method == null ? "GET" : method;
        String body = null;
        byte[] data = null;

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(finalMethod);
        // Set a User-Agent header to simulate a web browser request
        connection.setRequestProperty("User-Agent", "cis5550-crawler");
        connection.setInstanceFollowRedirects(false);

        int responseCode = connection.getResponseCode();
        Map<String, List<String>> headers = connection.getHeaderFields();

        if (responseCode < 400) {
            data = getStreamBodyBytes(connection.getInputStream());
            body = new String(data, StandardCharsets.UTF_8);
        }
        else {
            body = headers.get(null).get(0).replaceAll("HTTP/1.1 ", "");
            data = body.getBytes();
        }

        HttpCrawlResponse response = new HttpCrawlResponse();
        response.content = body;
        response.data = data;
        response.httpCode = responseCode;
        // Add a case-insensitive map
        Map <String, List<String>> treeMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((key, value) -> {
            if(key != null) treeMap.put(key, value);
        });

        response.headers = treeMap;
        return response;
    }

    public static String extractHeader(Map<String, List<String>> headers, String headerName) {
        return headers.get(headerName).get(0);
    }

    public static String hashUrl(String url) {
        return Hasher.hash(url);
    }

    public static ArrayList<String> sliceStringArray(String[] inputArray, int start, int end) {
        int finalEnd = end == 0 ? inputArray.length : end;
        // Allocate an array the size of end-start
        String[] outputSlice = new String[finalEnd - start];

        // Copy elements of arr to slice
        System.arraycopy(inputArray, start, outputSlice, 0, outputSlice.length);

        // Use a hash set to remove possible duplicates, then convert back to array
        HashSet<String> outputSet = new HashSet<>(Arrays.asList(outputSlice));
        return new ArrayList<>(outputSet);
    }

    public static synchronized HashSet<String> loadWordsFromFile(String filePath) {
        String commentIdentifier = "#!comment:";

        HashSet<String> wordSet = new HashSet<>();

        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                // Ignore lines that start with the comment identifier
                if (!line.trim().startsWith(commentIdentifier)) {
                    // Trim leading and trailing whitespaces and add the word to the set
                    wordSet.add(line.trim());
                }
            }
        } catch (IOException e) {
            System.out.println(e);
            e.printStackTrace(); // Handle or log the exception based on your application's needs
        }

        return wordSet;
    }

    /**
     // Example usage
     HashMap<String, Integer> exampleHashMap = new HashMap<>();
     exampleHashMap.put("one", 1);
     exampleHashMap.put("two", 2);
     exampleHashMap.put("three", 3);

     String serializedString = serializeHashMap(exampleHashMap);
     System.out.println("Serialized HashMap: " + serializedString);

     HashMap<String, Integer> deserializedHashMap = deserializeHashMap(serializedString);
     System.out.println("Deserialized HashMap: " + deserializedHashMap);

     */

    public static HashMap<String, Integer> deserializeHashMap(String serializedString) {
        HashMap<String, Integer> deserializedHashMap = new HashMap<>();

        // Split the serialized string into key-value pairs
        String[] keyValuePairs = serializedString.split(",");

        // Iterate through the pairs and populate the HashMap
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            String key = Hasher.hash(keyValuePairs[i]);
            int value = Integer.parseInt(keyValuePairs[i + 1]);

            deserializedHashMap.put(key, value);
        }

        return deserializedHashMap;
    }

    public static String serializeHashMap(HashMap<String, Integer> hashMap) {
        StringBuilder serializedString = new StringBuilder();

        for (Map.Entry<String, Integer> entry : hashMap.entrySet()) {
            // Append key and value separated by a comma
            serializedString.append(entry.getKey()).append(",").append(entry.getValue()).append(",");
        }

        // Remove the trailing comma if there is at least one entry
        if (serializedString.length() > 0) {
            serializedString.deleteCharAt(serializedString.length() - 1);
        }

        return serializedString.toString();
    }


    public static String stemWord(String term) {
        PorterStemmer porterStemmer = new PorterStemmer();
        char[] charArray = term.toLowerCase().trim().toCharArray();
        porterStemmer.add(charArray, charArray.length);
        porterStemmer.stem();
        return porterStemmer.toString();
    }

    public static String[] getWords(String document) {
        String[] firstSet = document
                .toLowerCase()
                .replaceAll("\\p{Punct}", "")
                .split("\\s+");

        return Arrays
                .stream(firstSet)
                .filter(word -> !stopWords.contains(word))
                .filter(word -> !word.isEmpty())
                .map(Helpers::stemWord)
                .toArray(String[]::new);
    }

    public static String getKvsDefault(KVSClient kvs, String table, String rowKey, String columnName, String defaultVal) throws IOException {
        try {
            byte[] data = kvs.get(table, rowKey, columnName);
            return new String(data, StandardCharsets.UTF_8);
        } catch(Exception e) {
            return defaultVal;
        }
    }

    public static String encode64(String input ) {
        return Base64.getEncoder().encodeToString(input.getBytes());
    }

    public static String decode64(String encodedString) {
        byte[] decodedBytes = Base64.getDecoder().decode(encodedString);
        return  new String(decodedBytes);
    }

}
