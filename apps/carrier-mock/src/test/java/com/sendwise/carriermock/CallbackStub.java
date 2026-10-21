package com.sendwise.carriermock;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class CallbackStub {

    private final HttpServer server;
    private final int port;
    private final List<Hit> hits = new CopyOnWriteArrayList<>();

    private CallbackStub(HttpServer server) {
        this.server = server;
        this.port = server.getAddress().getPort();
    }

    static CallbackStub start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            CallbackStub stub = new CallbackStub(server);
            server.createContext("/", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                stub.hits.add(new Hit(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        new String(body, StandardCharsets.UTF_8)));
                byte[] ok = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, ok.length);
                exchange.getResponseBody().write(ok);
                exchange.close();
            });
            server.start();
            return stub;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    List<Hit> hits() {
        return hits;
    }

    void close() {
        server.stop(0);
    }

    record Hit(String method, String path, String body) {
    }
}
