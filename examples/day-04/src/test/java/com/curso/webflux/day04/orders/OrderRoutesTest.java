package com.curso.webflux.day04.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Endpoints funcionales de pedidos con WebTestClient.
 * Los pedidos usan los productos 1 y 2 (hay stock de sobra) y el 4 solo en líneas que fallan.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class OrderRoutesTest {

    @Autowired
    WebTestClient client;

    @Test
    void createsOrderAndReadsItBack() {
        var body = Map.of("customerId", "cliente-rutas",
                "details", List.of(Map.of("productId", "1", "quantity", 1), Map.of("productId", "2", "quantity", 3)));

        Order created = client.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", "http://localhost:\\d+/api/orders/.+")
                .expectBody(Order.class)
                .returnResult()
                .getResponseBody();

        client.get().uri("/api/orders/{id}", created.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.customerId").isEqualTo("cliente-rutas")
                .jsonPath("$.details.length()").isEqualTo(2)
                .jsonPath("$.details[0].productName").isEqualTo("Teclado mecánico");

        // Total = suma de subtotales (no se compara con un literal: el ticker SSE puede cambiar precios)
        BigDecimal subtotals = created.details().stream().map(OrderDetail::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(created.total()).isEqualByComparingTo(subtotals);

        client.get().uri("/api/orders?customerId={c}", "cliente-rutas")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Order.class).hasSize(1);
    }

    @Test
    void businessErrorsAre422ProblemDetailWithEveryLine() {
        var body = Map.of("customerId", "c",
                "details", List.of(Map.of("productId", "99", "quantity", 1), Map.of("productId", "4", "quantity", 50)));

        client.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://curso-webflux.example/problems/order-rejected")
                .jsonPath("$.instance").isEqualTo("/api/orders")
                .jsonPath("$.errors.length()").isEqualTo(2)
                .jsonPath("$.errors[0].message").isEqualTo("el producto no existe")
                .jsonPath("$.errors[1].productId").isEqualTo("4");
    }

    @Test
    void structuralErrorsAre400WithFieldNames() {
        var body = Map.of("customerId", "",
                "details", List.of(Map.of("productId", "1", "quantity", 0), Map.of("productId", "1", "quantity", 2)));

        client.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.errors.customerId").exists()
                .jsonPath("$.errors['details[0].quantity']").exists()
                .jsonPath("$.errors.withoutRepeatedProducts").isEqualTo("no puede haber productos repetidos en el pedido");
    }

    @Test
    void malformedJsonAndUnknownPropertiesAre400() {
        client.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"customerId\":")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);

        client.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"customerId\":\"c\",\"details\":[{\"productId\":\"1\",\"quantiy\":1}]}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void unknownOrderIs404ProblemDetail() {
        client.get().uri("/api/orders/{id}", "no-existe")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.orderId").isEqualTo("no-existe");
    }

    /** En el modelo funcional un predicado que no se cumple = ninguna ruta coincide = 404 (no 415). */
    @Test
    void unmatchedPredicateMeansNoRoute() {
        client.post().uri("/api/orders")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("hola")
                .exchange()
                .expectStatus().isNotFound();
    }
}
