package com.curso.webflux.day04.interop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.curso.webflux.day04.catalog.Product;
import com.curso.webflux.day04.catalog.ProductNotFoundException;
import com.curso.webflux.day04.catalog.ProductService;
import com.curso.webflux.day04.error.GlobalExceptionHandler;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Interoperabilidad con otras bibliotecas reactivas.
 *
 * El controlador se prueba con WebTestClient.bindToController(...): se instancia a mano (con un servicio de
 * Mockito) y se le añade el @ControllerAdvice. Ni contexto de Spring ni servidor: WebFlux mínimo alrededor
 * de UN controlador. Es la opción intermedia entre un test unitario puro y @WebFluxTest.
 */
class InteropTest {

    static final Product WEBCAM = new Product("7", "Webcam", "video", new BigDecimal("100.00"), 3, 0L);
    static final Product MOUSE = new Product("8", "Ratón", "perifericos", new BigDecimal("10.00"), 50, 0L);

    ProductService service;
    WebTestClient client;

    @BeforeEach
    void setUp() {
        service = mock(ProductService.class);
        client = WebTestClient.bindToController(new InteropController(service))
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void flowableIsWrittenLikeAFlux() {
        when(service.findAll(any())).thenReturn(Flux.just(WEBCAM, MOUSE));

        client.get().uri("/api/interop/rx/low-stock?max=10")
                .accept(MediaType.APPLICATION_NDJSON)                  // también en streaming
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Product.class)
                .value(products -> assertThat(products).extracting(Product::id).containsExactly("7"));
    }

    @Test
    void singleErrorGoesThroughTheControllerAdvice() {
        when(service.findById("7")).thenReturn(Mono.just(WEBCAM));
        when(service.findById("99")).thenReturn(Mono.error(new ProductNotFoundException("99")));

        client.get().uri("/api/interop/rx/products/7")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.priceWithVat").isEqualTo(121.00);

        client.get().uri("/api/interop/rx/products/99")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void completableFutureIsAlsoSupported() {
        when(service.findAll(any())).thenReturn(Flux.just(WEBCAM, MOUSE));

        client.get().uri("/api/interop/future/count")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Long.class).isEqualTo(2L);
    }

    /**
     * Lo que hace WebFlux por dentro con cada tipo de retorno: preguntar al ReactiveAdapterRegistry.
     * El adaptador sabe si el tipo es de uno o varios valores y cómo convertirlo a/desde un Publisher.
     */
    @Test
    void reactiveAdapterRegistryConvertsBetweenLibraries() {
        ReactiveAdapterRegistry registry = ReactiveAdapterRegistry.getSharedInstance();

        ReactiveAdapter single = registry.getAdapter(Single.class);
        ReactiveAdapter flowable = registry.getAdapter(Flowable.class);
        ReactiveAdapter future = registry.getAdapter(CompletableFuture.class);
        assertThat(single.isMultiValue()).isFalse();
        assertThat(flowable.isMultiValue()).isTrue();
        assertThat(future.isMultiValue()).isFalse();
        assertThat(registry.getAdapter(String.class)).isNull();       // no es un tipo reactivo

        // RxJava -> Publisher (lo que WebFlux hace para escribir la respuesta)
        StepVerifier.create(single.toPublisher(Single.just("de RxJava")))
                .expectNext("de RxJava")
                .verifyComplete();

        // Publisher -> RxJava (lo que hace para un @RequestBody Single<T>)
        Flowable<?> fromReactor = (Flowable<?>) flowable.fromPublisher(Flux.just(1, 2, 3));
        assertThat(fromReactor.count().blockingGet()).isEqualTo(3);  // blocking*: solo en un test
    }
}
