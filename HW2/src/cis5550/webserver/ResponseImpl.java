package cis5550.webserver;

import java.util.Map;

public class ResponseImpl implements Response {
    String body;
    byte[] bodyRaw;
    Map<String, String> headers;
    int status;
    String reason;
    public ResponseImpl() {
        this.reason = "OK";
        this.status = 200;
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
        if (this.headers.containsKey("Connection") && this.headers.get("Connection").equals("close")) {
            // the subsequent calls
        } else {
            // the first call
            this.header("Connection", "close");

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
