package cis5550.jobs;

class RobotRules {

    double CrawlDelay;
    boolean ValidAgent;

    AllowedRule allowedRule;

    public AllowedRule getAllowedRule() {
        return allowedRule;
    }

    public void setAllowedRule(AllowedRule allowedRule) {
        this.allowedRule = allowedRule;
    }

    public DisallowedRule getDisallowedRule() {
        return disallowedRule;
    }

    public void setDisallowedRule(DisallowedRule disallowedRule) {
        this.disallowedRule = disallowedRule;
    }

    DisallowedRule disallowedRule;

    public boolean isValidAgent() {
        return ValidAgent;
    }

    public void setValidAgent(boolean validAgent) {
        ValidAgent = validAgent;
    }

    public RobotRules() {
    }

    public double getCrawlDelay() {
        return CrawlDelay;
    }

    public void setCrawlDelay(double crawlDelay) {
        CrawlDelay = crawlDelay;
    }
}

class AllowedRule {
    public AllowedRule(String allowedHost, int priority) {
        AllowedHost = allowedHost;
        Priority = priority;
    }

    String AllowedHost;
    int Priority;
}

class DisallowedRule {
    public DisallowedRule(String disallowedHost, int priority) {
        DisallowedHost = disallowedHost;
        Priority = priority;
    }

    String DisallowedHost;
    int Priority;
}
