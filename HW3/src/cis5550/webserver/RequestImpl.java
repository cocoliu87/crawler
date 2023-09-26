package cis5550.webserver;

import java.util.*;
import java.net.*;
import java.nio.charset.*;

// Provided as part of the framework code

class RequestImpl implements Request {
  String method;
  String url;
  String protocol;
  InetSocketAddress remoteAddr;
  Map<String,String> headers;
  Map<String,String> queryParams;
  Map<String,String> params;
  byte bodyRaw[];
  Server server;

  public String getSessionId() {
    return sessionId;
  }

  String sessionId;

  RequestImpl(String methodArg, String urlArg, String protocolArg, Map<String,String> headersArg, Map<String,String> queryParamsArg, Map<String,String> paramsArg, InetSocketAddress remoteAddrArg, byte bodyRawArg[], Server serverArg) {
    method = methodArg;
    url = urlArg;
    remoteAddr = remoteAddrArg;
    protocol = protocolArg;
    headers = headersArg;
    queryParams = queryParamsArg;
    params = paramsArg;
    bodyRaw = bodyRawArg;
    server = serverArg;
  }

  public String requestMethod() {
  	return method;
  }
  public void setParams(Map<String,String> paramsArg) {
    params = paramsArg;
  }
  public int port() {
  	return remoteAddr.getPort();
  }
  public String url() {
  	return url;
  }
  public String protocol() {
  	return protocol;
  }
  public String contentType() {
  	return headers.get("content-type");
  }
  public String ip() {
  	return remoteAddr.getAddress().getHostAddress();
  }
  public String body() {
    return new String(bodyRaw, StandardCharsets.UTF_8);
  }
  public byte[] bodyAsBytes() {
  	return bodyRaw;
  }
  public int contentLength() {
  	return bodyRaw.length;
  }
  public String headers(String name) {
  	return headers.get(name.toLowerCase());
  }
  public Set<String> headers() {
  	return headers.keySet();
  }
  public String queryParams(String param) {
  	return queryParams.get(param);
  }
  public Set<String> queryParams() {
  	return queryParams.keySet();
  }
  public String params(String param) {
    return params.get(param);
  }
  public Map<String,String> params() {
    return params;
  }

  public Session session() {
    int defaultActiveInterval = 300;
    SessionImpl s = null;
    if (headers.containsKey("cookie")) {
      String pair = headers.get("cookie");
      String[] parts = pair.split("=");
      if (parts.length == 2 && parts[0].equals("SessionID")) {
        if (this.server.sessions.containsKey(parts[1])) {
          s = (SessionImpl) this.server.sessions.get(parts[1]);
          long now = System.currentTimeMillis();
          if (s.getLastAccessedTime() < now - s.getAvailableInSecond()*1000L) {
            String oldId = s.getId();
            s = new SessionImpl(defaultActiveInterval);
            this.server.sessions.remove(oldId);
            this.server.sessions.put(s.getId(), s);
          } else {
            s.setLastAccessedTime(System.currentTimeMillis());
          }
        } else {
          s = new SessionImpl(defaultActiveInterval);
          this.server.sessions.put(s.id(), s);
        }
      }
    } else {
      s = new SessionImpl(defaultActiveInterval);;
      this.server.sessions.put(s.id(), s);
    }
    assert s != null;
    this.sessionId = s.getId();
    return s;
  }

}
