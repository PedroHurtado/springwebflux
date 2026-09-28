package com.curso.webflux.day01.catalog;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.test.StepVerifier;

/**
 * Pruebas de integración con WebTestClient (se profundiza el día 4).
 * Arranca la aplicación completa y lanza peticiones HTTP reales.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ProductControllerTest {

    @Autowired
    WebTestClient client;

    @Test
    void listsProductsAsJsonArray() {
        client.get().uri("/api/products")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Response-Time")
                .expectBodyList(Product.class).hasSize(5);
    }

    @Test
    void filtersByCategory() {
        client.get().uri(uri -> uri.path("/api/products").queryParam("category", "perifericos").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].category").isEqualTo("perifericos");
    }

    @Test
    void streamsProductsAsNdjson() {
        var body = client.get().uri("/api/products")
                .accept(MediaType.APPLICATION_NDJSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
                .returnResult(Product.class)
                .getResponseBody();

        StepVerifier.create(body).expectNextCount(5).verifyComplete();
    }

    @Test
    void returns404WhenProductDoesNotExist() {
        client.get().uri("/api/products/{id}", "no-existe")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void createReadAndDeleteProduct() {
        var request = new ProductRequest("Webcam", "perifericos", new BigDecimal("39.90"), 12);

        Product created = client.post().uri("/api/products")
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", "/api/products/.+")
                .expectBody(Product.class)
                .returnResult()
                .getResponseBody();

        client.get().uri("/api/products/{id}", created.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.name").isEqualTo("Webcam");

        // Limpieza: el repositorio en memoria es compartido por todos los tests
        client.delete().uri("/api/products/{id}", created.id())
                .exchange()
                .expectStatus().isNoContent();

        client.delete().uri("/api/products/{id}", created.id())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void rejectsInvalidProduct() {
        var invalid = new ProductRequest("", "perifericos", new BigDecimal("-1"), 1);

        client.post().uri("/api/products")
                .bodyValue(invalid)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void emitsPriceChangesAsServerSentEvents() {
        var events = client.get().uri("/api/products/prices")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(ProductService.PriceChange.class)
                .getResponseBody();

        StepVerifier.create(events.take(2))
                .expectNextCount(2)
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void dispatcherInternalsEndpointListsSpecialBeans() {
        client.get().uri("/internals/dispatcher")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body)
                        .contains("RequestMappingHandlerMapping")
                        .contains("RouterFunctionMapping")
                        .contains("ResponseBodyResultHandler"));
    }
}
