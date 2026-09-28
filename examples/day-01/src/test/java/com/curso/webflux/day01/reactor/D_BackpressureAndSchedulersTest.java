package com.curso.webflux.day01.reactor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;

import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bloque 1/2 - Backpressure (request(n)) y Schedulers (publishOn / subscribeOn).
 */
class D_BackpressureAndSchedulersTest {

    @Test
    void subscriberControlsDemandWithRequest() {
        List<Integer> received = new ArrayList<>();

        Flux.range(1, 100)
                .log()
                .subscribe(new BaseSubscriber<Integer>() {
                    @Override
                    protected void hookOnSubscribe(Subscription subscription) {
                        request(2); // solo pido 2 elementos
                    }

                    @Override
                    protected void hookOnNext(Integer value) {
                        received.add(value);
                        if (value == 2) {
                            cancel(); // y cancelo: el publisher deja de producir
                        }
                    }
                });

        assertThat(received).containsExactly(1, 2);
    }

    @Test
    void stepVerifierCanRequestInSteps() {
        StepVerifier.create(Flux.range(1, 10), 3) // demanda inicial = 3
                .expectNext(1, 2, 3)
                .thenRequest(2)
                .expectNext(4, 5)
                .thenCancel()
                .verify();
    }

    @Test
    void limitRateSplitsUpstreamDemand() {
        StepVerifier.create(Flux.range(1, 10).log().limitRate(4)) // pide al origen de 4 en 4
                .expectNextCount(10)
                .verifyComplete();
    }

    @Test
    void overflowStrategyWhenProducerIsFaster() {
        // interval emite por tiempo y no puede "frenar": si el consumidor es lento hay que decidir
        // qué hacer con lo que sobra (buffer, drop, latest, error). Aquí: descartar.
        Flux<Long> slowConsumer = Flux.interval(Duration.ofMillis(1))
                .onBackpressureDrop()
                .concatMap(n -> Mono.just(n).delayElement(Duration.ofMillis(20)), 1) // procesa de 1 en 1, lento
                .take(5);

        StepVerifier.create(slowConsumer.collectList())
                .assertNext(values -> assertThat(values.get(4)).isGreaterThan(4L)) // se han perdido ticks
                .verifyComplete();
    }

    @Test
    void publishOnSwitchesThreadForDownstream() {
        List<String> threads = Collections.synchronizedList(new ArrayList<>());

        StepVerifier.create(Flux.range(1, 2)
                        .doOnNext(n -> threads.add("antes:" + Thread.currentThread().getName()))
                        .publishOn(Schedulers.parallel())
                        .doOnNext(n -> threads.add("despues:" + Thread.currentThread().getName())))
                .expectNextCount(2)
                .verifyComplete();

        assertThat(threads).filteredOn(t -> t.startsWith("antes:")).allMatch(t -> t.contains("main"));
        assertThat(threads).filteredOn(t -> t.startsWith("despues:")).allMatch(t -> t.contains("parallel"));
    }

    @Test
    void subscribeOnAffectsTheSourceWherever() {
        List<String> threads = Collections.synchronizedList(new ArrayList<>());

        StepVerifier.create(Mono.fromCallable(() -> {
                            threads.add(Thread.currentThread().getName()); // p.ej. llamada JDBC bloqueante
                            return "dato";
                        })
                        .map(String::toUpperCase)
                        .subscribeOn(Schedulers.boundedElastic())) // da igual dónde se coloque
                .expectNext("DATO")
                .verifyComplete();

        assertThat(threads).allMatch(t -> t.startsWith("boundedElastic"));
    }
}
