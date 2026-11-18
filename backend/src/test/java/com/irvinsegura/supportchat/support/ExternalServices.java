package com.irvinsegura.supportchat.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * One local HTTP server standing in for every outside API the platform calls (Meta's Graph API, Stripe, ...), each
 * under its own path prefix. It records every request and answers with what a test registered for the method and
 * path, or with {@code {}}.
 * <p>
 * Tests that use it extend {@link WithExternalServices}, so they all share one application context.
 */
public final class ExternalServices {

    private static final ConcurrentLinkedQueue<Request> REQUESTS = new ConcurrentLinkedQueue<>();
    private static final Map<String, Function<Request, Response>> ROUTES = new ConcurrentHashMap<>();
    private static final HttpServer SERVER = start();

    private ExternalServices() {
    }

    public static String baseUrl() {
        return "http://127.0.0.1:" + SERVER.getAddress().getPort();
    }

    /**
     * Answers {@code method path} (the path without its query) with the given function from now on. A path ending in
     * {@code /*} covers every path under it.
     */
    public static void on(String method, String path, Function<Request, Response> answer) {
        ROUTES.put(method + " " + path, answer);
    }

    public static void on(String method, String path, String json) {
        on(method, path, request -> Response.json(200, json));
    }

    public static List<Request> requests(Predicate<Request> matching) {
        return REQUESTS.stream().filter(matching).toList();
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestURI().getRawQuery(), exchange.getRequestHeaders().getFirst("Authorization"),
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                REQUESTS.add(request);
                Response response = route(request).apply(request);
                byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(response.status(), body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Function<Request, Response> route(Request request) {
        String key = request.method() + " " + request.path();
        Function<Request, Response> exact = ROUTES.get(key);
        if (exact != null) {
            return exact;
        }
        for (String path = request.path(); path.lastIndexOf('/') > 0; ) {
            path = path.substring(0, path.lastIndexOf('/'));
            Function<Request, Response> under = ROUTES.get(request.method() + " " + path + "/*");
            if (under != null) {
                return under;
            }
        }
        return ignored -> Response.json(200, "{}");
    }

    /**
     * @param query the raw query string, or null
     */
    public record Request(String method, String path, String query, String authorization, String body) {

        /**
         * A form parameter of the body, decoded; null when absent.
         */
        public String form(String name) {
            for (String pair : body.split("&")) {
                String[] parts = pair.split("=", 2);
                if (decode(parts[0]).equals(name)) {
                    return parts.length > 1 ? decode(parts[1]) : "";
                }
            }
            return null;
        }

        private static String decode(String value) {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
    }

    public record Response(int status, String body) {

        public static Response json(int status, String body) {
            return new Response(status, body);
        }
    }
}
