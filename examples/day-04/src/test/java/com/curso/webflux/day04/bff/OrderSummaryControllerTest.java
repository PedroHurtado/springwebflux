package com.curso.webflux.day04.bff;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.curso.webflux.day04.bff.LineView.Status;

import reactor.test.StepVerifier;

/**
 * El BFF de extremo a extremo: WebTestClient -> servidor real (Reactor Netty) -> WebClient ->
 * microservicios simulados (FakeRemoteServices).
 *
 * Se usa el servidor real (RANDOM_PORT) a propósito: así las peticiones se atienden en los hilos del
 * event loop y se puede comprobar que block() ahí está prohibido.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "10s")
class OrderSummaryControllerTest {

    static final FakeRemoteServices REMOTE = new FakeRemoteServices();

    /** Las URLs de los "otros microservicios" apuntan al servidor simulado. */
    @DynamicPropertySource
    static void remoteServices(DynamicPropertyRegistry registry) {
        registry.add("app.services.catalog.base-url", REMOTE::baseUrl);
        registry.add("spring.http.serviceclient.orders.base-url", REMOTE::baseUrl);
    }

    @AfterAll
    static void stopRemote() {
        REMOTE.stop();
    }

    @Autowired
    WebTestClient client;

    @BeforeEach
    void resetRemote() {
        REMOTE.reset();
    }

    @Test
    void composesOrderAndCatalogInOneResponse() {
        client.get().uri("/api/bff/orders/o-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderSummary.class)
                .value(summary -> {
                    assertThat(summary.lines()).extracting(LineView::productId)
                            .containsExactly("p1", "p2", "p3");                   // flatMapSequential: orden del pedido
                    assertThat(summary.lines()).extracting(LineView::status)
                            .containsExactly(Status.AVAILABLE, Status.OUT_OF_STOCK, Status.AVAILABLE);
                    assertThat(summary.totalAtPurchase()).isEqualByComparingTo("135.00");
                    assertThat(summary.totalAtCurrentPrices()).isEqualByComparingTo("145.00"); // 100 + 2x20 + 5
                    assertThat(summary.complete()).isTrue();
                });
    }

    @Test
    void callsTheCatalogInParallel() {
        client.get().uri("/api/bff/orders/o-1").exchange().expectStatus().isOk();   // calentamiento (conexiones, JIT)

        long start = System.nanoTime();
        client.get().uri("/api/bff/orders/o-1").exchange().expectStatus().isOk();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        // 3 productos x 300 ms: en serie serían >= 900 ms; en paralelo, ~300 ms + la llamada a pedidos
        assertThat(elapsed).isLessThan(FakeRemoteServices.PRODUCT_LATENCY.multipliedBy(3));
    }

    @Test
    void propagatesTheCorrelationIdToEveryDownstreamCall() {
        client.get().uri("/api/bff/orders/o-1")
                .header("X-Request-Id", "curso-123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", "curso-123");

        // 1 llamada a pedidos + 3 al catálogo, todas con el mismo X-Request-Id (Context de Reactor)
        assertThat(REMOTE.receivedRequestIds()).hasSize(4).containsOnly("curso-123");
    }

    @Test
    void degradesInsteadOfFailingWhenTheCatalogMisbehaves() {
        client.get().uri("/api/bff/orders/o-2")
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderSummary.class)
                .value(summary -> {
                    assertThat(summary.lines()).extracting(LineView::status).containsExactly(
                            Status.AVAILABLE,       // p1
                            Status.DISCONTINUED,    // gone: 404 -> Mono vacío
                            Status.UNKNOWN,         // slow: timeout en los 3 intentos
                            Status.AVAILABLE);      // flaky: 503 y, al reintentar, 200
                    assertThat(summary.complete()).isFalse();
                });

        assertThat(REMOTE.callsTo("/api/products/gone")).isEqualTo(1);   // un 404 no se reintenta
        assertThat(REMOTE.callsTo("/api/products/flaky")).isEqualTo(2);  // 503 + reintento
        assertThat(REMOTE.callsTo("/api/products/slow")).isEqualTo(3);   // intento + 2 reintentos
    }

    @Test
    void streamsEnrichedLinesAsNdjson() {
        var lines = client.get().uri("/api/bff/orders/o-1/lines")
                .accept(MediaType.APPLICATION_NDJSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
                .returnResult(LineView.class)
                .getResponseBody();

        StepVerifier.create(lines.map(LineView::productId))
                .recordWith(java.util.ArrayList::new)
                .expectNextCount(3)
                .consumeRecordedWith(ids -> assertThat(ids).containsExactlyInAnyOrder("p1", "p2", "p3"))
                .verifyComplete();
    }

    @Test
    void translatesRemoteErrorsToOwnProblemDetails() {
        client.get().uri("/api/bff/orders/no-existe")                  // pedidos responde 404
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://curso-webflux.example/problems/order-not-found");

        client.get().uri("/api/bff/orders/boom")                       // pedidos responde 500
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://curso-webflux.example/problems/upstream-unavailable");
    }

    @Test
    void blockingInsideTheEventLoopIsRejected() {
        // IllegalStateException: block()/blockFirst()/blockLast() are blocking, which is not supported
        // in thread webflux-http-nio-N  (ver el log del test)
        client.get().uri("/api/bff/orders/o-1/blocking")
                .exchange()
                .expectStatus().is5xxServerError();
    }
}
