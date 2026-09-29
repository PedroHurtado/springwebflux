package com.curso.webflux.day02.orders;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Evidencia de la validación reactiva a través del controlador ANOTADO (/api/annotated/orders).
 * Las mismas peticiones que OrderRoutesTest (endpoint funcional) deben dar las mismas respuestas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class OrderControllerTest {

    @Autowired
    WebTestClient client;

    /** 201: todas las líneas existen y hay stock; Location apunta al propio controlador. */
    @Test
    void validOrderIsCreated() {
        var body = Map.of("customerId", "cliente-anotado",
                "details", List.of(Map.of("productId", "1", "quantity", 1), Map.of("productId", "2", "quantity", 2)));

        Order created = client.post().uri("/api/annotated/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", "http://localhost:\\d+/api/annotated/orders/.+")
                .expectBody(Order.class)
                .returnResult()
                .getResponseBody();

        client.get().uri("/api/annotated/orders/{id}", created.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.details.length()").isEqualTo(2);
    }

    /** 400: @Valid @RequestBody (validación estructural) -> GlobalExceptionHandler -> ProblemDetail. */
    @Test
    void structuralErrorsAre400() {
        var body = Map.of("customerId", "",
                "details", List.of(Map.of("productId", "1", "quantity", 0), Map.of("productId", "1", "quantity", 2)));

        client.post().uri("/api/annotated/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.errors.customerId").exists()
                .jsonPath("$.errors['details[0].quantity']").exists()
                .jsonPath("$.errors.withoutRepeatedProducts").exists();
    }

    /** 422: validación de negocio reactiva en OrderService (producto inexistente + stock insuficiente). */
    @Test
    void businessErrorsAre422WithEveryLine() {
        var body = Map.of("customerId", "c",
                "details", List.of(Map.of("productId", "99", "quantity", 1), Map.of("productId", "4", "quantity", 50)));

        client.post().uri("/api/annotated/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://curso-webflux.example/problems/order-rejected")
                .jsonPath("$.instance").isEqualTo("/api/annotated/orders")
                .jsonPath("$.errors.length()").isEqualTo(2)
                .jsonPath("$.errors[0].message").isEqualTo("el producto no existe")
                .jsonPath("$.errors[1].message").isEqualTo("stock insuficiente: solicitadas 50, disponibles 5");
    }

    @Test
    void unknownOrderIs404() {
        client.get().uri("/api/annotated/orders/{id}", "no-existe")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.orderId").isEqualTo("no-existe");
    }
}
