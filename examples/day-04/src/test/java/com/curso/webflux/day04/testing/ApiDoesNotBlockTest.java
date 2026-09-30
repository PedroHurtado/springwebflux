package com.curso.webflux.day04.testing;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.blockhound.BlockHound;

/**
 * BlockHound sobre la aplicación COMPLETA: si algún punto de la API bloquease un hilo del event loop
 * (una llamada síncrona escondida en un servicio, un driver bloqueante...), la petición fallaría con 500
 * (BlockingOperationError) y este test lo detectaría. Es una red de seguridad para el día a día.
 *
 * Recorre la API JSON: catálogo (Spring Data R2DBC) y pedidos (DatabaseClient + transacción reactiva).
 * Las vistas Thymeleaf NO se incluyen: Thymeleaf lee las plantillas del disco de forma síncrona al procesarlas
 * y BlockHound lo detecta (ver docs/day-04/04-pruebas.md).
 */
@Tag("blockhound")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ApiDoesNotBlockTest {

    @BeforeAll
    static void install() {
        BlockHound.install();
    }

    @Autowired
    WebTestClient client;

    @Test
    void catalogAndOrdersDoNotBlockTheEventLoop() {
        client.get().uri("/api/products").exchange().expectStatus().isOk();
        client.get().uri("/api/products/search?category=perifericos&sort=price-desc").exchange().expectStatus().isOk();
        client.get().uri("/api/products/1").exchange().expectStatus().isOk();

        client.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("customerId", "cliente-bh",
                        "details", List.of(Map.of("productId", "1", "quantity", 1), Map.of("productId", "2", "quantity", 2))))
                .exchange()
                .expectStatus().isCreated();

        client.get().uri("/api/orders?customerId=cliente-bh")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[0].details.length()").isEqualTo(2);
    }
}
