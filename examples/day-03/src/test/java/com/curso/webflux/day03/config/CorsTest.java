package com.curso.webflux.day03.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * CORS visto "desde el navegador": enviamos las cabeceras Origin / Access-Control-Request-*
 * que el navegador añadiría y comprobamos las Access-Control-Allow-* de la respuesta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class CorsTest {

    @Autowired
    WebTestClient client;

    /** Preflight (OPTIONS) de un POST JSON: la configuración global también cubre endpoints funcionales. */
    @Test
    void globalConfigAllowsPreflightForFunctionalEndpoints() {
        client.options().uri("/api/orders")
                .header("Origin", "http://127.0.0.1:8080")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://127.0.0.1:8080")
                .expectHeader().valueEquals("Access-Control-Max-Age", "1800");
    }

    @Test
    void unknownOriginIsRejected() {
        client.options().uri("/api/orders")
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .exchange()
                .expectStatus().isForbidden();
    }

    /** @CrossOrigin en ProductController añade un origen solo para /api/products/**. */
    @Test
    void crossOriginAnnotationAddsAnOriginToOneController() {
        client.get().uri("/api/products/count")
                .header("Origin", "http://localhost:5173")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173");

        client.get().uri("/api/orders")
                .header("Origin", "http://localhost:5173")
                .exchange()
                .expectStatus().isForbidden();
    }

    /** Sin cabecera Origin no es una petición CORS: no se añaden cabeceras. */
    @Test
    void sameOriginRequestsAreNotAffected() {
        client.get().uri("/api/products/count")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("Access-Control-Allow-Origin");
    }
}
