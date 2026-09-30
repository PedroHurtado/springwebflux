package com.curso.webflux.day04.testing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.curso.webflux.day04.catalog.PriceFeed;
import com.curso.webflux.day04.catalog.Product;
import com.curso.webflux.day04.catalog.ProductController;
import com.curso.webflux.day04.catalog.ProductImageStore;
import com.curso.webflux.day04.catalog.ProductNotFoundException;
import com.curso.webflux.day04.catalog.ProductRequest;
import com.curso.webflux.day04.catalog.ProductService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @WebFluxTest: "slice" de la capa web. Arranca SOLO lo necesario para los controladores indicados:
 * WebFlux, codecs de Jackson, validación, @ControllerAdvice, WebFluxConfigurer (WebConfig), WebFilter,
 * conversores... NO arranca servicios, repositorios, R2DBC ni un servidor HTTP (WebTestClient llama al
 * WebHandler directamente, sin red).
 *
 * Las dependencias del controlador se sustituyen con @MockitoBean (Spring Framework 6.2+; sustituye al
 * @MockBean de Spring Boot, eliminado en Boot 4): un doble de Mockito registrado como bean.
 *
 * Mucho más rápido que @SpringBootTest y aísla el controlador: si falla, el problema está en la capa web.
 */
@WebFluxTest(ProductController.class)
class ProductControllerSliceTest {

    @Autowired
    WebTestClient client;

    @MockitoBean
    ProductService service;

    @MockitoBean
    ProductImageStore images;

    @MockitoBean
    PriceFeed priceFeed;

    @Test
    void findByIdReturnsJsonAndAnETagBuiltFromTheVersion() {
        when(service.findById("7")).thenReturn(Mono.just(
                new Product("7", "Webcam", "video", new BigDecimal("39.90"), 3, 4L)));

        client.get().uri("/api/products/7")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("ETag", "\"7-v4\"")
                .expectBody()
                .jsonPath("$.name").isEqualTo("Webcam")
                .jsonPath("$.version").doesNotExist();            // @JsonIgnore: detalle de persistencia
    }

    /** El @RestControllerAdvice (GlobalExceptionHandler) forma parte del slice: el error llega como ProblemDetail. */
    @Test
    void serviceErrorIsTranslatedByTheControllerAdvice() {
        when(service.findById("99")).thenReturn(Mono.error(new ProductNotFoundException("99")));

        client.get().uri("/api/products/99")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.productId").isEqualTo("99");
    }

    /** Con datos no válidos el servicio ni se llama: la validación ocurre en la capa web. */
    @Test
    void invalidBodyNeverReachesTheService() {
        client.post().uri("/api/products")
                .bodyValue(new ProductRequest("", "video", new BigDecimal("-1"), 1))
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(service);
    }

    @Test
    void ndjsonStreamsWhatTheServiceEmits() {
        when(service.findAll(any())).thenReturn(Flux.just(
                new Product("1", "A", "c", BigDecimal.ONE, 1), new Product("2", "B", "c", BigDecimal.TEN, 2)));

        client.get().uri("/api/products")
                .accept(MediaType.APPLICATION_NDJSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
                .expectBodyList(Product.class).hasSize(2);
    }
}
