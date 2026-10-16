package com.infy.catalog.infra;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

/**
 * Kubernetes probes on the same port as the SOAP endpoint:
 * <ul>
 *   <li>GET /health/live  - 200 while the process can serve HTTP (liveness/startup probe)</li>
 *   <li>GET /health/ready - 200 when started, not shutting down and the database answers (readiness)</li>
 * </ul>
 */
public final class HealthHttpHandler implements HttpHandler {

    private final BooleanSupplier ready;

    public HealthHttpHandler(BooleanSupplier ready) {
        this.ready = ready;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                respond(exchange, 405, "{\"status\":\"METHOD_NOT_ALLOWED\"}", false);
                return;
            }
            boolean head = "HEAD".equals(method);
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/live")) {
                respond(exchange, 200, "{\"status\":\"UP\"}", head);
            } else if (path.endsWith("/ready")) {
                boolean up = ready.getAsBoolean();
                respond(exchange, up ? 200 : 503, up ? "{\"status\":\"UP\"}" : "{\"status\":\"DOWN\"}", head);
            } else {
                respond(exchange, 404, "{\"status\":\"NOT_FOUND\"}", head);
            }
        }
    }

    private static void respond(HttpExchange exchange, int status, String json, boolean headOnly) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if (headOnly) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
