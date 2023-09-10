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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

class MyHttpHandler implements HttpHandler {
    String customizedPath;
    MyHttpHandler(String path) {
        customizedPath = path;
    }

    final String IMAGE = "image/jpeg", TEXT = "text/plain", HTML = "text/html", STREAM = "application/octet-stream";

    static Logger log = Logger.getLogger(MyHttpHandler.class);

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestParamValue;
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
//                  requestParamValue = "405 Not Allowed";
                  requestParamValue = "";
                  statusCode = 405;
              }
//                requestParamValue = handleGetRequest(exchange);
              default -> {
//                  requestParamValue = "501 Not Implemented";
                  requestParamValue = "";
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
        String message = this.customizedPath + exchange.getRequestURI().toString();
        File f = new File(message);
        String statusCode = "200";

        if (!f.exists() && !f.isDirectory()) {
            statusCode = "404";
//            message = "404 Not Found";
            message = "";
        } else if (!f.canRead()) {
            statusCode = "403";
//            message = "403 Forbidden";
            message = "";
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
        OutputStream outputStream = exchange.getResponseBody();
        Headers headers = exchange.getResponseHeaders();
        String htmlResponse = "";
        if (statusCode == 200 && !requestParamValue.isEmpty()) {
            htmlResponse = new Reader().ReadFileToString(requestParamValue);
        }



        log.info("Sending response");
//        headers.add("Content-Length", Long.toString(htmlResponse.length()));
        String[] splitted = requestParamValue.split("\\.");
        String type = splitted[splitted.length-1];
        String contentType;
        switch (type) {
            case "jpg", "jpeg" -> {
                contentType = IMAGE;
                break;
            }
            case "text" -> {
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
        headers.add("Content-Type", contentType);
        headers.add("Server", "hw");
        String size = String.valueOf((long)(htmlResponse.length()));
        headers.add("Content-Length", size);
        exchange.sendResponseHeaders(statusCode, htmlResponse.length());
        outputStream.write(htmlResponse.getBytes());
        outputStream.flush();
        outputStream.close();
    }
}
