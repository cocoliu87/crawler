package cis5550.webserver;

public interface Condition {
    void operate(Request req, Response resp);
}
