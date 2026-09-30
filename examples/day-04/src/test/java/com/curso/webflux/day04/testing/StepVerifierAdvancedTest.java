package com.curso.webflux.day04.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import com.curso.webflux.day04.catalog.Product;
import com.curso.webflux.day04.catalog.ProductRepository;
import com.curso.webflux.day04.catalog.ProductService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * StepVerifier más allá de expectNext/verifyComplete (el día 1 vimos lo básico):
 *   - tiempo virtual con un flujo INFINITO basado en Flux.interval (el ticker de precios);
 *   - control de la demanda (backpressure) desde el test: create(publisher, 0) + thenRequest(n);
 *   - comprobaciones sobre la propia verificación: verifyThenAssertThat();
 *   - depuración: checkpoint() añade al error la descripción del punto del pipeline donde se montó.
 */
class StepVerifierAdvancedTest {

    static final Product KEYBOARD = new Product("1", "Teclado mecánico", "perifericos", new BigDecimal("100.00"), 25, 0L);

    ProductRepository repository;
    ProductService service;

    @BeforeEach
    void setUp() {
        repository = mock(ProductRepository.class);
        when(repository.findRandom()).thenAnswer(call -> Mono.just(KEYBOARD));
        when(repository.updatePrice(anyString(), any())).thenReturn(Mono.just(1));
        when(repository.findAll(any(Sort.class))).thenReturn(Flux.fromStream(() -> IntStream.rangeClosed(1, 100)
                .mapToObj(i -> KEYBOARD.withId(String.valueOf(i)))));
        service = new ProductService(repository);
    }

    /**
     * El ticker emite un cambio por segundo, para siempre. Con tiempo real, comprobar 1 hora de ticks
     * tardaría 1 hora; con tiempo virtual, milisegundos. withVirtualTime recibe un Supplier: el Flux se
     * CREA dentro, cuando Flux.interval ya usa el VirtualTimeScheduler.
     */
    @Test
    void priceTickerInVirtualTime() {
        StepVerifier.withVirtualTime(() -> service.priceTicker())
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(999))                // nada antes del primer segundo
                .thenAwait(Duration.ofMillis(1))
                .assertNext(change -> {
                    assertThat(change.productId()).isEqualTo("1");
                    assertThat(change.newPrice()).isBetween(new BigDecimal("95.00"), new BigDecimal("105.00"));
                })
                .thenAwait(Duration.ofHours(1))                       // "pasa" una hora
                .expectNextCount(3600)
                .thenCancel()                                         // flujo infinito: se cancela
                .verify(Duration.ofSeconds(5));
    }

    /**
     * Backpressure: el test hace de suscriptor lento y pide los elementos de dos en dos.
     * create(publisher, 0): demanda inicial CERO; nada llega hasta el primer thenRequest.
     */
    @Test
    void theSubscriberControlsTheDemand() {
        StepVerifier.create(service.findAll(null), 0)
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(100))
                .thenRequest(2)
                .expectNextCount(2)
                .expectNoEvent(Duration.ofMillis(100))                // no hay más demanda: no llega nada
                .thenRequest(2)
                .expectNextMatches(product -> product.id().equals("3"))
                .expectNextMatches(product -> product.id().equals("4"))
                .thenCancel()
                .verify();
    }

    /** verifyThenAssertThat: comprobaciones sobre la ejecución (duración, elementos descartados...). */
    @Test
    void assertionsAboutTheVerificationItself() {
        StepVerifier.create(service.findAll(null).take(10))
                .expectNextCount(10)
                .expectComplete()
                .verifyThenAssertThat(Duration.ofSeconds(1))
                .tookLessThan(Duration.ofMillis(500))
                .hasNotDroppedElements()
                .hasNotDroppedErrors();
    }

    /**
     * En un pipeline reactivo la traza de un error apunta a las clases de Reactor, no a TU código.
     * checkpoint("...") añade al error (como excepción "suprimida") la descripción del punto donde se
     * montó ese tramo. Alternativas globales: Hooks.onOperatorDebug() (cara) o ReactorDebugAgent
     * (spring.reactor.debug-agent.enabled, requiere reactor-tools).
     */
    @Test
    void checkpointHelpsToFindWhereAnErrorComesFrom() {
        when(repository.findById("x")).thenReturn(Mono.error(new IllegalStateException("BD caída")));

        StepVerifier.create(service.findById("x").checkpoint("ProductService.findById desde el test"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).hasMessage("BD caída");
                    assertThat(error.getSuppressed()).singleElement()
                            .satisfies(assembly -> assertThat(assembly.getMessage())
                                    .contains("checkpoint", "ProductService.findById desde el test"));
                })
                .verify();
    }
}
