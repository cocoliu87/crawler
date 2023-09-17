package cis5550.webserver;

public class Routing {
    public enum Verb {
        GET,
        POST,
        PUT;
    }

    Verb method;
    String pathPattern;
    Route route;
    public Routing(Verb v, String p, Route r) {
        this.method = v;
        this.pathPattern = p;
        this.route = r;
    }
}
