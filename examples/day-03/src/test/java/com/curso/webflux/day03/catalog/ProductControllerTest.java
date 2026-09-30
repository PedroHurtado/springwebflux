package com.curso.webflux.day03.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;

import reactor.test.StepVerifier;

/**
 * Controlador anotado (parte II) con WebTestClient contra el servidor real.
 * Los pedidos de OrderRoutesTest pueden descontar stock de los productos 1 y 2: aquí no se depende de él.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ProductControllerTest {

    @Autowired
    WebTestClient client;

    // ---------- Básicos (día 1) ----------

    @Test
    void listsProductsAsJsonAndNdjson() {
        client.get().uri("/api/products")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Response-Time")
                .expectBodyList(Product.class).hasSize(5);

        var body = client.get().uri("/api/products")
                .accept(MediaType.APPLICATION_NDJSON)
                .exchange()
                .expectStatus().isOk()
                .returnResult(Product.class)
                .getResponseBody();
        StepVerifier.create(body).expectNextCount(5).verifyComplete();
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

    // ---------- Errores como ProblemDetail ----------

    @Test
    void unknownProductIs404ProblemDetail() {
        client.get().uri("/api/products/{id}", "no-existe")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://curso-webflux.example/problems/product-not-found")
                .jsonPath("$.title").isEqualTo("Producto no encontrado")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.instance").isEqualTo("/api/products/no-existe")
                .jsonPath("$.productId").isEqualTo("no-existe");
    }

    @Test
    void invalidBodyIs400WithOneEntryPerField() {
        var invalid = new ProductRequest("", "perifericos", new BigDecimal("-1"), 1);

        client.post().uri("/api/products")
                .bodyValue(invalid)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.errors.name").exists()
                .jsonPath("$.errors.price").exists()
                .jsonPath("$.errors.category").doesNotExist();
    }

    @Test
    void unknownJsonPropertyIs400() {
        client.post().uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "X", "category", "c", "price", 1, "stock", 1, "color", "rojo"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ---------- URI: Location absoluta ----------

    @Test
    void createReturnsAbsoluteLocation() {
        Product created = client.post().uri("/api/products")
                .bodyValue(new ProductRequest("Webcam", "perifericos", new BigDecimal("39.90"), 12))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", "http://localhost:\\d+/api/products/.+")
                .expectBody(Product.class)
                .returnResult()
                .getResponseBody();

        client.delete().uri("/api/products/{id}", created.id())   // limpieza: repositorio compartido
                .exchange()
                .expectStatus().isNoContent();
    }

    // ---------- Data binding, conversión y validación de parámetros ----------

    @Test
    void searchBindsQueryParamsIntoARecordAndConvertsSort() {
        client.get().uri("/api/products/search?category=perifericos&maxPrice=100&sort=price-desc")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo("1")     // teclado (más caro) primero
                .jsonPath("$[1].id").isEqualTo("2");
    }

    @Test
    void searchReportsConversionAndValidationErrors() {
        client.get().uri("/api/products/search?sort=raro")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors.sort").isEqualTo("valor no válido: 'raro'");

        client.get().uri("/api/products/search?maxPrice=-5")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors.maxPrice").exists();
    }

    @Test
    void topValidatesItsRequestParam() {
        client.get().uri("/api/products/top?limit=2")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Product.class).hasSize(2);

        client.get().uri("/api/products/top?limit=50")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.errors.limit").exists();
    }

    // ---------- API versioning ----------

    @Test
    void sameResourceInTwoApiVersions() {
        Product v1 = client.get().uri("/api/products/5")          // sin cabecera -> versión 1.0 por defecto
                .exchange()
                .expectStatus().isOk()
                .expectBody(Product.class)
                .returnResult()
                .getResponseBody();

        BigDecimal expectedWithVat = v1.price().multiply(new BigDecimal("1.21")).setScale(2, RoundingMode.HALF_UP);
        client.get().uri("/api/products/5")
                .header("API-Version", "2.0")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.stock").doesNotExist()
                .jsonPath("$.priceWithVat").isEqualTo(expectedWithVat.doubleValue())
                .jsonPath("$.warning").doesNotExist();              // stock alto -> null -> NON_NULL lo omite

        client.get().uri("/api/products/4")
                .header("API-Version", "2.0")
                .exchange()
                .expectBody().jsonPath("$.warning").isEqualTo("Quedan 5 unidades");

        client.get().uri("/api/products/5")
                .header("API-Version", "3.0")
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ---------- Multipart ----------

    @Test
    void uploadsAndDownloadsAnImage() {
        byte[] png = { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3 };

        client.post().uri("/api/products/1/image")
                .body(BodyInserters.fromMultipartData(imagePart(png, "teclado.png", MediaType.IMAGE_PNG)))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.filename").isEqualTo("teclado.png")
                .jsonPath("$.size").isEqualTo(png.length);

        client.get().uri("/api/products/1/image")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.IMAGE_PNG)
                .expectBody(byte[].class).isEqualTo(png);
    }

    @Test
    void rejectsNonImagesAndTooLargeFiles() {
        client.post().uri("/api/products/1/image")
                .body(BodyInserters.fromMultipartData(imagePart("hola".getBytes(), "a.txt", MediaType.TEXT_PLAIN)))
                .exchange()
                .expectStatus().isEqualTo(415);

        byte[] big = new byte[ProductImageStore.MAX_IMAGE_BYTES + 1];
        client.post().uri("/api/products/1/image")
                .body(BodyInserters.fromMultipartData(imagePart(big, "grande.png", MediaType.IMAGE_PNG)))
                .exchange()
                .expectStatus().isEqualTo(413);

        client.post().uri("/api/products/no-existe/image")
                .body(BodyInserters.fromMultipartData(imagePart(new byte[] { 1 }, "x.png", MediaType.IMAGE_PNG)))
                .exchange()
                .expectStatus().isNotFound();
    }

    private static MultiValueMap<String, HttpEntity<?>> imagePart(byte[] bytes, String filename, MediaType type) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).contentType(type);
        return builder.build();
    }
}
