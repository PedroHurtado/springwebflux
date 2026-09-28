package com.curso.webflux.day01.reactor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bloque 2 - Bibliotecas reactivas: Mono y Flux.
 *
 * Mono<T>: 0..1 elementos. Flux<T>: 0..N elementos.
 * Ambos implementan org.reactivestreams.Publisher<T>.
 */
class A_MonoFluxBasicsTest {

    @Test
    void nothingHappensUntilYouSubscribe() {
        AtomicInteger calls = new AtomicInteger();
        Mono<Integer> mono = Mono.fromSupplier(calls::incrementAndGet);

        // Hemos DECLARADO el flujo, pero nadie se ha suscrito
        assertThat(calls.get()).isZero();

        // Cada suscripción ejecuta de nuevo el Supplier (publisher "frío")
        mono.subscribe();
        mono.subscribe();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void justIsEagerDeferIsLazy() {
        AtomicInteger counter = new AtomicInteger();

        Mono<Integer> eager = Mono.just(counter.incrementAndGet()); // se evalúa YA
        Mono<Integer> lazy = Mono.defer(() -> Mono.just(counter.incrementAndGet())); // se evalúa al suscribirse

        assertThat(counter.get()).isEqualTo(1);
        StepVerifier.create(eager).expectNext(1).verifyComplete();
        StepVerifier.create(lazy).expectNext(2).verifyComplete();
        StepVerifier.create(lazy).expectNext(3).verifyComplete();
    }

    @Test
    void signalsOnNextOnCompleteOnError() {
        List<String> signals = new ArrayList<>();

        Flux.just("a", "b")
                .concatWith(Mono.error(new IllegalStateException("boom")))
                .subscribe(
                        value -> signals.add("onNext(" + value + ")"),
                        error -> signals.add("onError(" + error.getMessage() + ")"),
                        () -> signals.add("onComplete")); // no se llama: error y complete son excluyentes

        assertThat(signals).containsExactly("onNext(a)", "onNext(b)", "onError(boom)");
    }

    @Test
    void stepVerifierIsTheWayToTestPublishers() {
        Flux<Integer> numbers = Flux.range(1, 5);

        StepVerifier.create(numbers)
                .expectNext(1, 2)
                .expectNextCount(2)
                .expectNext(5)
                .verifyComplete();
    }

    @Test
    void emptyAndErrorAreValidOutcomes() {
        StepVerifier.create(Mono.empty()).verifyComplete();
        StepVerifier.create(Mono.error(new IllegalArgumentException("x")))
                .verifyError(IllegalArgumentException.class);
    }

    @Test
    void factoryMethods() {
        StepVerifier.create(Flux.fromIterable(List.of("x", "y"))).expectNext("x", "y").verifyComplete();
        StepVerifier.create(Mono.justOrEmpty(null)).verifyComplete();
        StepVerifier.create(Mono.fromCallable(() -> "desde Callable")).expectNext("desde Callable").verifyComplete();
        StepVerifier.create(Flux.generate(() -> 0, (state, sink) -> {
                    sink.next(state);
                    if (state == 3) {
                        sink.complete();
                    }
                    return state + 1;
                }))
                .expectNext(0, 1, 2, 3)
                .verifyComplete();
    }
}
