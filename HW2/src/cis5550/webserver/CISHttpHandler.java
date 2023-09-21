package cis5550.webserver;

import cis5550.filehandler.Reader;
import cis5550.tools.Logger;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;


import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.net.URLDecoder;

import static cis5550.tools.Constants.Header.*;
import static cis5550.tools.Utils.copyByteArray;
import static cis5550.tools.Utils.isInteger;
import static cis5550.webserver.Server.port;

class CISHttpHandler implements HttpHandler {
    String customizedPath;
    Server server;
    CISHttpHandler(String path, Server server) {
        this.server = server;
        this.customizedPath = path;
    }

    final String IMAGE = "image/jpeg", TEXT = "text/plain", HTML = "text/html", STREAM = "application/octet-stream";

    static Logger log = Logger.getLogger(CISHttpHandler.class);

    @Override
    public synchronized void handle(HttpExchange exchange) throws IOException {
        // checking coming request
        for (Routing r: Server.rt) {
            if (exchange.getRequestMethod().equalsIgnoreCase(r.method.name())){
                if (exchange.getRequestURI().toString().toLowerCase().startsWith(r.pathPattern) || r.pathPattern.contains(":")) {
                    Map<String, String> headers = new HashMap<>();
                    Headers reqHeaders = exchange.getRequestHeaders();
                    for (String key : reqHeaders.keySet()) {
                        String value = reqHeaders.get(key).get(0);
                        headers.put(key.toLowerCase(), value);
                    }

                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String bodyQuery = "";

                    // if Content-Type is set to 'application/x-www-form-urlencoded'
                    if (reqHeaders.containsKey(CONTENT_TYPE) && !reqHeaders.get(CONTENT_TYPE).isEmpty() && reqHeaders.get(CONTENT_TYPE).get(0).equals("application/x-www-form-urlencoded")) {
                        bodyQuery = body;
                    }

                    Map<String, String> queryMap = processURLQueryString(exchange.getRequestURI().toString(), bodyQuery);
                    Request req = new RequestImpl(
                            exchange.getRequestMethod(),
                            exchange.getRequestURI().toString(),
                            exchange.getProtocol(),
                            headers,
                            queryMap,
                            getParamsMap(exchange.getRequestURI().toString(), r.pathPattern),
                            new InetSocketAddress(port),
                            body.getBytes(),
                            this.server);

                    try {
                        if (!r.pathPattern.equals("/write")) {
                            ResponseImpl resp = new ResponseImpl();
                            Object response = r.route.handle(req, resp);
                            for (Map.Entry<String, String> entry : resp.headers.entrySet()) {
                                exchange.getResponseHeaders().put(entry.getKey().toLowerCase(), new ArrayList<>(Collections.singletonList(entry.getValue())));
                            }

                            handleResponse(exchange, (String) response, 200);
                        } else {
                            ResponseImpl resp = new ResponseImpl(exchange);
                            r.route.handle(req, resp);
                        }

                    } catch (Exception e) {
                        log.error(e.toString());
//                        handleResponse(exchange, exchange.getRequestBody().toString(), 200);
                        handleResponse(exchange, "500 Internal Server Error", 500);
                    }
                }
            }
        }

        handleFileResponse(exchange);

    }

    private String[] handleGetRequest(HttpExchange exchange) {
        // TODO: implementing the HW required GET request process
        log.info("handle GET request");

        // invalid request, return earlier
        if (exchange.getRequestURI().toString().contains("..")) {
            return new String[]{"403 Forbidden", "403"};
        }

        String message = this.customizedPath + exchange.getRequestURI().toString();
        String statusCode = "200";

        File f = new File(message);
        if (!f.exists() && !f.isDirectory()) {
            statusCode = "404";
            message = "404 Not Found";
        } else if (!f.canRead()) {
            statusCode = "403";
            message = "403 Forbidden";
        } else if (exchange.getRequestHeaders().containsKey(IF_MODIFIED_SINCE)) {
            // handle If-Modified-Since header
            if (!exchange.getRequestHeaders().get(IF_MODIFIED_SINCE).isEmpty()) {
                String dateStr = exchange.getRequestHeaders().get(IF_MODIFIED_SINCE).get(0);
                SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
                try {
                    long headerDate = format.parse(dateStr).getTime();
                    if (headerDate >= f.lastModified()) {
                        return new String[]{"304 Not Modified", "304"};
                    }
                } catch (ParseException ignored) {
                    log.info("The request header If-Modified-Since has invalid format, will ignore");
                }

            }
        }
        return new String[]{message, statusCode};
    }

    private String[] handleHeadRequest(HttpExchange exchange) {
        log.info("handle HEAD request");
        return new String[]{"", "200"};
    }

    private synchronized void handleResponse(HttpExchange exchange, String requestParamValue, int statusCode) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.add(SERVER, Server.SERVER_NAME);
        int size = 0;
        byte[] bytes = new byte[]{};
        if (requestParamValue != null) {
            bytes = requestParamValue.getBytes();
            size = bytes.length;
        }
        headers.add(CONTENT_LENGTH, String.valueOf(size));

        exchange.sendResponseHeaders(statusCode, size);

        assert requestParamValue != null;
        if (!requestParamValue.isEmpty()) {
            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(bytes);
            outputStream.flush();
            outputStream.close();
        }
    }

    private synchronized void handleFileResponse(HttpExchange exchange) throws IOException {
        String requestParamValue = "";
        int statusCode;
        if (exchange.getRequestHeaders() == null) {
            requestParamValue = "400 Bad Request";
            statusCode = 400;
        } else if (!Objects.equals(exchange.getProtocol(), "HTTP/1.1")) {
            requestParamValue = "505 HTTP Version Not Supported";
            statusCode = 505;
        } else {
            switch (exchange.getRequestMethod()) {
                case "GET" -> {
                    String[] values = handleGetRequest(exchange);
                    requestParamValue = values[0];
                    statusCode = Integer.parseInt(values[1]);
                }
                case "HEAD" -> {
                    String[] values = handleHeadRequest(exchange);
                    requestParamValue = values[0];
                    statusCode = Integer.parseInt(values[1]);
                }
                case "POST", "PUT" -> {
                    requestParamValue = "";
                    statusCode = 200;
                }
                default -> {
                    requestParamValue = "501 Not Implemented";
                    statusCode = 501;
                }
            }
        };

        Headers headers = exchange.getResponseHeaders();

        String[] params = exchange.getRequestURI().toString().split("\\.");
        String type = params[params.length-1];
        String contentType;
        switch (type) {
            case "jpg", "jpeg" -> {
                contentType = IMAGE;
                break;
            }
            case "txt" -> {
                contentType = TEXT;
                break;
            }
            case "html" -> {
                contentType = HTML;
                break;
            }
            default -> {
                contentType = STREAM;
            }
        }

        byte[] bytes = requestParamValue.getBytes();
        if (statusCode == 200 && !requestParamValue.isEmpty()) {
            switch (type) {
                case "jpg", "jpeg" -> {
                    bytes = new Reader().ReadImageFile(requestParamValue);
                    break;
                }
                case "txt", "html" -> {
                    bytes = new Reader().ReadTxtFile(requestParamValue);
                    break;
                }
                default -> {
                    bytes = new Reader().ReadBinaryFile(requestParamValue);
                }
            }
        }

        headers.add(CONTENT_TYPE, contentType);
        headers.add(SERVER, Server.SERVER_NAME);

        // handle Range header
        if (exchange.getRequestHeaders().containsKey("Range")) {
            // only handle single range
            String value = exchange.getRequestHeaders().get(RANGE).get(0);
            if (value.startsWith("bytes")) {
                // example "Range: bytes=0-1023"
                String range = value.split("=")[1];
                String[] parts = range.split("-");
                int start = -1;
                int end = -1;
                if (parts.length == 1 && isInteger(parts[0])) {
                    start = 0;
                    end = Integer.parseInt(parts[0]);
                    if (end < bytes.length) {
                        bytes = copyByteArray(bytes, start, end);
                        // Content-Range: bytes 0-1023/146515
                        headers.add(CONTENT_RANGE, "bytes " + start + "-" + end);
                    }
                } else if (parts.length == 2) {
                    if (parts[0].isEmpty() && isInteger(parts[1])) {
                        // such as -100
                        start = bytes.length - Integer.parseInt(parts[1]);
                        end = bytes.length - 1;
                        bytes = copyByteArray(bytes, start, end);
                        headers.add("Content-Range", "bytes " + start + "-" + end);
                    } else {
                        // such as 10-100
                        if (isInteger(parts[0]) && isInteger(parts[1])) {
                            start = Integer.parseInt(parts[0]);
                            end = Integer.parseInt(parts[1]);
                            if (end >= start && end < bytes.length) {
                                bytes = copyByteArray(bytes, start, end);
                                headers.add("Content-Range", "bytes " + start + "-" + end);
                            }
                        }
                    }
                } else {
                    log.warn("Invalid range, will ignore");
                }
            } else {
                log.warn("Invalid range header value, will ignore");
            }
        }

        long size = bytes == null ? 0 : bytes.length;
        headers.add(CONTENT_LENGTH, String.valueOf(size));

        exchange.sendResponseHeaders(statusCode, size);

        OutputStream outputStream = exchange.getResponseBody();
        assert bytes != null;
        outputStream.write(bytes);
        outputStream.flush();
        outputStream.close();
    }

    private Map<String, String> getParamsMap(String url, String pattern) {
        String[] urlParts = url.split("/");
        String[] pParts = pattern.split("/");
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < pParts.length; i++) {
            if (pParts[i].startsWith(":")) {
                String key = pParts[i].split(":")[1];
                params.put(key, urlParts[i]);
            }
        }

        return params;
    }

    private Map<String, String> processURLQueryString(String uri, String body) {
        Map<String, String> map = new HashMap<>();
        List<String> strs = new ArrayList<>();
        if (uri.contains("?")){
            String[] query = uri.split("\\?(?!\\?)");
            // Currently only consider single question mark
            String queryStr = query[query.length-1];
            String[] pairs = queryStr.split("&");
            strs.addAll(Arrays.asList(pairs));
        }

        if (!body.isEmpty()) {
            String[] bodyQueries = body.split("&");
            strs.addAll(Arrays.asList(bodyQueries));
        }

        for (String str: strs) {
            String decodedStr = URLDecoder.decode(str, StandardCharsets.UTF_8);
            String[] splitted = decodedStr.split("=", 2);
//            map.put(splitted[0], map.getOrDefault(splitted[0], "") + ", " + splitted[1]);
            if (map.containsKey(splitted[0])) {
                map.computeIfPresent(splitted[0], (k, v) -> v + ", " + splitted[1]);
            } else {
                map.put(splitted[0], splitted[1]);
            }
        }

        return map;
    }
}
