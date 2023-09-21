package cis5550.webserver;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.OutputStream;
import java.util.*;

public class ResponseImpl implements Response {
    String body;
    byte[] bodyRaw;
    Map<String, String> headers;
    int status;
    String reason;

    boolean initWrite;

    HttpExchange exchange;
    public ResponseImpl() {
        this.headers = new HashMap<>();
        this.bodyRaw = new byte[]{};
        this.body = "";
        this.reason = "OK";
        this.status = 200;
    }

    public ResponseImpl(HttpExchange exchange) {
        this();
        this.exchange = exchange;
        this.initWrite = true;
    }

    @Override
    public void body(String body) {
        this.body = body;
    }

    @Override
    public void bodyAsBytes(byte[] bodyArg) {
        this.bodyRaw = bodyArg;
    }

    @Override
    public void header(String name, String value) {
        this.headers.put(name, value);
    }

    @Override
    public void type(String contentType) {
        this.header("Content-Type", contentType);
    }

    @Override
    public void status(int statusCode, String reasonPhrase) {
        this.status = statusCode;
        this.reason = reasonPhrase;
    }

    @Override
    public void write(byte[] b) throws Exception {
        if (!this.initWrite) {
            // the subsequent calls
            if (b.length > 0) {
//                long size = b.length;
                String bStr = new String(b);
                OutputStream stream = exchange.getResponseBody();
                stream.write(b);
            }
        } else {
            // the first call
            this.header("Connection", "close");
            if (this.exchange != null) {
                Headers headers = exchange.getResponseHeaders();
                for (Map.Entry<String, String> entry: this.headers.entrySet()) {
                    headers.put(entry.getKey(), Collections.singletonList(entry.getValue()));
                }
//                long size = b.length;
                exchange.sendResponseHeaders(200, 0);

                this.initWrite = false;
                this.write(b);
            }
        }
    }

    @Override
    public void redirect(String url, int responseCode) {
        return;
    }

    @Override
    public void halt(int statusCode, String reasonPhrase) {
        return;
    }
}
