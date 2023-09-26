package cis5550.webserver;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionImpl implements Session {

    public int getAvailableInSecond() {
        return availableInSecond;
    }

    public void setLastAccessedTime(long time) {
        lastAccessedTime = time;
    }

    public String getId() {
        return id;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public int availableInSecond;

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastAccessedTime() {
        return lastAccessedTime;
    }

    public boolean isValidSession() {
        return validSession;
    }

    private final long createdAt;
    private long lastAccessedTime;
    private boolean validSession;
    private final String id;

    private Map<String, Object> attributes;

    public SessionImpl(int activateInterval) {
        this.createdAt = System.currentTimeMillis();
        this.lastAccessedTime = this.createdAt;
        this.id = UUID.randomUUID().toString();
        this.validSession = true;
        attributes = new HashMap<>();
//        this.maxActiveInterval(activateInterval);
    }

    @Override
    public String id() {
        return this.id;
    }

    @Override
    public long creationTime() {
        return this.createdAt;
    }

    @Override
    public long lastAccessedTime() {
        return this.lastAccessedTime;
    }

    @Override
    public void maxActiveInterval(int seconds) {
        this.availableInSecond = seconds;
    }

    @Override
    public void invalidate() {
        this.validSession = false;
    }

    @Override
    public Object attribute(String name) {
        return this.attributes.getOrDefault(name, null);
    }

    @Override
    public void attribute(String name, Object value) {
        this.attributes.put(name, value);
    }
}
