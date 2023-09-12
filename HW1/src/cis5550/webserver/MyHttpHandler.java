package cis5550.webserver;

import cis5550.filehandler.Reader;
import cis5550.tools.Logger;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

//import org.apache.commons.text.StringEscapeUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

class MyHttpHandler implements HttpHandler {
    String customizedPath;
    MyHttpHandler(String path) {
        customizedPath = path;
    }

    final String IMAGE = "image/jpeg", TEXT = "text/plain", HTML = "text/html", STREAM = "application/octet-stream";

    static Logger log = Logger.getLogger(MyHttpHandler.class);

    @Override
    public void handle(HttpExchange exchange) throws IOException {
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
                  requestParamValue = "405 Not Allowed";
                  statusCode = 405;
              }
              default -> {
                  requestParamValue = "501 Not Implemented";
                  statusCode = 501;
              }
          }
        };

//        if("GET".equals(exchange.getRequestMethod())) {
//            requestParamValue = handleGetRequest(exchange);
//        } else if("POST".equals(exchange.getRequestMethod())) {
//            requestParamValue = handlePostRequest(exchange);
//        }

        handleResponse(exchange, requestParamValue, statusCode);
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
        } else if (exchange.getRequestHeaders().containsKey("If-Modified-Since")) {
            // handle If-Modified-Since header
            if (!exchange.getRequestHeaders().get("If-Modified-Since").isEmpty()) {
                String dateStr = exchange.getRequestHeaders().get("If-Modified-Since").get(0);
                SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
                try {
                    Date headerDate = format.parse(dateStr);
                    Date fileDate = new Date(TimeUnit.SECONDS.toMillis(f.lastModified()));
                    if (fileDate.after(headerDate)) {
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

    private String[] handlePostRequest(HttpExchange exchange) {
        // TODO: implementing the HW required GET request process
        log.info("handle POST request");
        return new String[]{exchange.getRequestURI().toString(), "200"};
    }

    private void handleResponse(HttpExchange exchange, String requestParamValue, int statusCode) throws IOException {
        // handle requested content length
        Headers reqHeaders = exchange.getRequestHeaders();
        List<String> lenStr = reqHeaders.get("Content-Length");

        Headers headers = exchange.getResponseHeaders();

//        log.info("Sending response");
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

        headers.add("Content-Type", contentType);
        headers.add("Server", "hw");

        long size = bytes.length;
        headers.add("Content-Length", String.valueOf(size));

        // handle Range header
        if (exchange.getRequestHeaders().containsKey("Range")) {
            // only handle single range
            String value = exchange.getResponseHeaders().get("Range").get(0);
            // example "Range: bytes=0-1023"
            String range = value.split("=")[1];
            String[] parts = range.split("-");
            int start = -1;
            int end = -1;
            if (parts.length == 1 && isInteger(parts[0])) {
                start = 0;
                end = Integer.parseInt(parts[0]);
                bytes = copyByteArray(bytes, start, end);
            } else if (parts.length == 2) {
                if (parts[0].isEmpty() && isInteger(parts[1])) {
                    // such as -100
                    start = bytes.length - Integer.parseInt(parts[1]);
                    end = bytes.length - 1;
                    bytes = copyByteArray(bytes, start, end);
                } else {
                    // such as 10-100
                    if (isInteger(parts[0]) && isInteger(parts[1])) {
                        start = Integer.parseInt(parts[0]);
                        end = Integer.parseInt(parts[1]);
                        if (end >= start) {
                            bytes = copyByteArray(bytes, start, end);
                        }
                    }
                }
            } else {
                log.warn("Invalid range, will ignore");
            }
        }

        exchange.sendResponseHeaders(statusCode, size);

        OutputStream outputStream = exchange.getResponseBody();
        outputStream.write(bytes);
        outputStream.flush();
        outputStream.close();
    }

    public boolean isInteger(String str) {
        try {
            Integer.parseInt(str);
        } catch(NumberFormatException | NullPointerException e) {
            return false;
        }
        // only got here if we didn't return false
        return true;
    }

    public byte[] copyByteArray(byte[] ori, int start, int end) {
        byte[] copy = new byte[end-start+1];
        while (start <= end) {
            copy[start] = ori[start];
            start++;
        }
        return copy;
    }
}
