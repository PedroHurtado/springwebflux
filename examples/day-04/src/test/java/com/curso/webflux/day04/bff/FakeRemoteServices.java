package com.curso.webflux.day04.bff;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import com.curso.webflux.day04.client.CorrelationId;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

/**
 * Los microservicios "remotos" de pedidos y catálogo, simulados en los tests con un servidor
 * Reactor Netty mínimo (viene con WebFlux: no hace falta ninguna dependencia extra).
 *
 * Permite controlar lo que en un entorno real no se controla: latencia, 404, 500, 503 intermitentes
 * y respuestas que no llegan a tiempo. Además registra las cabeceras X-Request-Id recibidas.
 *
 * Pedidos:  o-1 -> p1 x1, p2 x2, p3 x1           (todo bien, 300 ms por producto)
 *           o-2 -> p1 x1, gone x1, slow x1, flaky x1 (un fallo de cada tipo)
 *           boom -> 500 · cualquier otro -> 404
 * Catálogo: p1, p2 (stock 1), p3 -> 200 tras 300 ms · gone -> 404 · slow -> 3 s (timeout)
 *           flaky -> 503 en los intentos impares, 200 en los pares (el reintento lo arregla)
 */
final class FakeRemoteServices {

    static final Duration PRODUCT_LATENCY = Duration.ofMillis(300);

    private static final Map<String, String> PRODUCTS = Map.of(
            "p1", product("p1", "Teclado", "100.00", 10),
            "p2", product("p2", "Ratón", "20.00", 1),
            "p3", product("p3", "Alfombrilla", "5.00", 50),
            "flaky", product("flaky", "Webcam", "7.00", 5),
            "slow", product("slow", "Micrófono", "30.00", 5));

    private static final Map<String, String> ORDERS = Map.of(
            "o-1", order("o-1", "135.00", line("p1", "Teclado", "90.00", 1), line("p2", "Ratón", "20.00", 2),
                    line("p3", "Alfombrilla", "5.00", 1)),
            "o-2", order("o-2", "137.00", line("p1", "Teclado", "90.00", 1), line("gone", "Cable", "10.00", 1),
                    line("slow", "Micrófono", "30.00", 1), line("flaky", "Webcam", "7.00", 1)));

    private final DisposableServer server;
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
    private final Queue<String> receivedRequestIds = new ConcurrentLinkedQueue<>();

    FakeRemoteServices() {
        this.server = HttpServer.create()
                .host("localhost")
                .port(0)                                                     // puerto libre cualquiera
                .route(routes -> routes
                        .get("/api/orders/{id}", this::order)
                        .get("/api/products/{id}", this::product))
                .bindNow();
    }

    String baseUrl() {
        return "http://localhost:" + server.port();
    }

    int callsTo(String path) {
        return calls.getOrDefault(path, new AtomicInteger()).get();
    }

    List<String> receivedRequestIds() {
        return List.copyOf(receivedRequestIds);
    }

    void reset() {
        calls.clear();
        receivedRequestIds.clear();
    }

    private Mono<Void> order(HttpServerRequest request, HttpServerResponse response) {
        String id = register(request);
        if ("boom".equals(id)) {
            return response.status(500).send();
        }
        String json = ORDERS.get(id);
        return json == null ? response.status(404).send() : json(response, Mono.just(json));
    }

    private Mono<Void> product(HttpServerRequest request, HttpServerResponse response) {
        String id = register(request);
        int attempt = callsTo(request.fullPath());
        return switch (id) {
            case "gone" -> response.status(404).send();
            case "slow" -> json(response, Mono.just(PRODUCTS.get(id)).delayElement(Duration.ofSeconds(3)));
            case "flaky" -> attempt % 2 == 1
                    ? response.status(503).send()
                    : json(response, Mono.just(PRODUCTS.get(id)));
            default -> PRODUCTS.containsKey(id)
                    ? json(response, Mono.just(PRODUCTS.get(id)).delayElement(PRODUCT_LATENCY))   // latencia SIN bloquear
                    : response.status(404).send();
        };
    }

    private String register(HttpServerRequest request) {
        calls.computeIfAbsent(request.fullPath(), path -> new AtomicInteger()).incrementAndGet();
        String requestId = request.requestHeaders().get(CorrelationId.HEADER);
        if (requestId != null) {
            receivedRequestIds.add(requestId);
        }
        return request.param("id");
    }

    private static Mono<Void> json(HttpServerResponse response, Mono<String> body) {
        return response.header("Content-Type", "application/json").sendString(body).then();
    }

    private static String product(String id, String name, String price, int stock) {
        // "category" no está en ProductDto: el cliente debe ignorarla (tolerant reader)
        return """
                {"id":"%s","name":"%s","category":"demo","price":%s,"stock":%d}""".formatted(id, name, price, stock);
    }

    private static String order(String id, String total, String... lines) {
        return """
                {"id":"%s","customerId":"cliente-bff","createdAt":"2026-09-30T08:00:00Z","details":[%s],"total":%s}"""
                .formatted(id, String.join(",", lines), total);
    }

    private static String line(String productId, String name, String unitPrice, int quantity) {
        return """
                {"productId":"%s","productName":"%s","unitPrice":%s,"quantity":%d,"subtotal":0}"""
                .formatted(productId, name, unitPrice, quantity);
    }

    void stop() {
        server.disposeNow();
    }
}
