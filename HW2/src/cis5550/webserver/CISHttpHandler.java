package cis5550.webserver;

import cis5550.filehandler.Reader;
import cis5550.tools.Logger;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;


import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Objects;

import static cis5550.tools.Constants.Header.*;
import static cis5550.tools.Utils.copyByteArray;
import static cis5550.tools.Utils.isInteger;

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
    public void handle(HttpExchange exchange) throws IOException {

        // checking coming request
        for (Routing r: Server.rt) {
            if (exchange.getRequestMethod().equalsIgnoreCase(r.method.name())){
                if (exchange.getRequestURI().toString().equalsIgnoreCase(r.pathPattern)) {
                    Request req = new RequestImpl();
                    Response resp = new ResponseImpl();

                }
            }
        }

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

    private String[] handlePostRequest(HttpExchange exchange) {
        // TODO: implementing the HW required GET request process
        log.info("handle POST request");
        return new String[]{exchange.getRequestURI().toString(), "200"};
    }

    private void handleResponse(HttpExchange exchange, String requestParamValue, int statusCode) throws IOException {
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

        long size = bytes.length;
        headers.add(CONTENT_LENGTH, String.valueOf(size));

        exchange.sendResponseHeaders(statusCode, size);

        OutputStream outputStream = exchange.getResponseBody();
        outputStream.write(bytes);
        outputStream.flush();
        outputStream.close();
    }
}
