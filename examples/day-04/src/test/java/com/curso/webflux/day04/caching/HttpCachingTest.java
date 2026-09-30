package com.curso.webflux.day04.caching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import com.curso.webflux.day04.orders.Order;

/**
 * Caché HTTP: Cache-Control, validadores (ETag, Last-Modified), peticiones condicionales (304)
 * y Range requests (206).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class HttpCachingTest {

    @Autowired
    WebTestClient client;

    @Test
    void mutableProductIsRevalidatedWithETag() {
        String etag = client.get().uri("/api/products/3")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().cacheControl(org.springframework.http.CacheControl.noCache())
                .expectHeader().exists(HttpHeaders.ETAG)
                .returnResult(String.class)
                .getResponseHeaders().getETag();

        client.get().uri("/api/products/3")
                .ifNoneMatch(etag)                                         // "¿sigue siendo esta versión?"
                .exchange()
                .expectStatus().isNotModified()                            // 304: sin cuerpo
                .expectBody().isEmpty();

        client.get().uri("/api/products/3")
                .ifNoneMatch("\"otra-version\"")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void immutableOrderIsCacheableAndConditional() {
        Order order = client.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("customerId", "cliente-cache",
                        "details", List.of(Map.of("productId", "5", "quantity", 1))))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Order.class).returnResult().getResponseBody();

        HttpHeaders headers = client.get().uri("/api/orders/{id}", order.id())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "max-age=3600, private")
                .returnResult(String.class)
                .getResponseHeaders();
        assertThat(headers.getETag()).isEqualTo("\"" + order.id() + "\"");
        assertThat(headers.getLastModified()).isPositive();

        client.get().uri("/api/orders/{id}", order.id())                  // endpoint FUNCIONAL: también 304
                .ifNoneMatch(headers.getETag())
                .exchange()
                .expectStatus().isNotModified();

        client.get().uri("/api/orders/{id}", order.id())
                .header(HttpHeaders.IF_MODIFIED_SINCE, headers.getFirst(HttpHeaders.LAST_MODIFIED))
                .exchange()
                .expectStatus().isNotModified();
    }

    @Test
    void staticResourcesUseBootCacheProperties() {
        String lastModified = client.get().uri("/index.html")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "max-age=600, public")
                .expectHeader().exists(HttpHeaders.LAST_MODIFIED)
                .returnResult(String.class)
                .getResponseHeaders().getFirst(HttpHeaders.LAST_MODIFIED);

        client.get().uri("/index.html")
                .header(HttpHeaders.IF_MODIFIED_SINCE, lastModified)
                .exchange()
                .expectStatus().isNotModified();
    }

    @Test
    void imageSupportsRangeRequests() {
        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("file", new ClassPathResource("teclado.png")).contentType(MediaType.IMAGE_PNG);
        client.post().uri("/api/products/2/image")
                .body(BodyInserters.fromMultipartData(multipart.build()))
                .exchange()
                .expectStatus().isCreated();

        long size = client.get().uri("/api/products/2/image")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCEPT_RANGES, "bytes")
                .returnResult(byte[].class)
                .getResponseHeaders().getContentLength();

        client.get().uri("/api/products/2/image")
                .header(HttpHeaders.RANGE, "bytes=0-9")                    // solo los 10 primeros bytes
                .exchange()
                .expectStatus().isEqualTo(206)                             // Partial Content
                .expectHeader().valueEquals(HttpHeaders.CONTENT_RANGE, "bytes 0-9/" + size)
                .expectBody(byte[].class)
                .value(bytes -> assertThat(bytes).hasSize(10)
                        .startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G'));   // firma de un PNG
    }
}
