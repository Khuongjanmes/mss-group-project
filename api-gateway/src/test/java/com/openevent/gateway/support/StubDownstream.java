package com.openevent.gateway.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Service giả: ghi lại đúng những gì Gateway chuyển xuống. */
public final class StubDownstream {

    public record Received(String method, String rawPath, String rawQuery, Headers headers, String body) {
        public String header(String name) {
            return headers.getFirst(name);
        }

        public List<String> headerValues(String name) {
            return headers.getOrDefault(name, List.of());
        }

        public boolean hasHeader(String name) {
            return headers.containsKey(name);
        }
    }

    private final List<Received> requests = new CopyOnWriteArrayList<>();
    private final HttpServer server;

    public StubDownstream() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::handle);
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
                exchange.getRequestURI().getRawQuery(), exchange.getRequestHeaders(), body));
        if (exchange.getRequestURI().getPath().endsWith("/slow")) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] response = "{\"reached\":true}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void clear() {
        requests.clear();
    }

    public List<Received> requests() {
        return requests;
    }

    public Received last() {
        return requests.getLast();
    }

    public static Map<String, String> noHeaders() {
        return Map.of();
    }
}
