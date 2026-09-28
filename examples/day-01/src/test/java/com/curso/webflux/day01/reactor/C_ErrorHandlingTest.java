package com.curso.webflux.day01.reactor;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

/**
 * Bloque 2 - Los errores son señales (onError), no excepciones lanzadas.
 * Un try/catch alrededor de un Flux NO captura nada: se usan operadores.
 */
class C_ErrorHandlingTest {

    @Test
    void onErrorReturnGivesFallbackValue() {
        StepVerifier.create(Flux.just(1, 2, 0, 4).map(n -> 10 / n).onErrorReturn(-1))
                .expectNext(10, 5, -1) // tras el error la secuencia TERMINA
                .verifyComplete();
    }

    @Test
    void onErrorResumeSwitchesToAnotherPublisher() {
        Mono<String> primary = Mono.error(new IllegalStateException("servicio caído"));
        Mono<String> fallback = Mono.just("respuesta de caché");

        StepVerifier.create(primary.onErrorResume(IllegalStateException.class, e -> fallback))
                .expectNext("respuesta de caché")
                .verifyComplete();
    }

    @Test
    void onErrorMapTranslatesExceptions() {
        StepVerifier.create(Mono.error(new IllegalArgumentException("bajo nivel"))
                        .onErrorMap(e -> new RuntimeException("error de negocio", e)))
                .verifyErrorMessage("error de negocio");
    }

    @Test
    void retryResubscribes() {
        AtomicInteger attempts = new AtomicInteger();
        Mono<String> flaky = Mono.fromCallable(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("fallo " + attempts.get());
            }
            return "ok al intento " + attempts.get();
        });

        StepVerifier.create(flaky.retryWhen(Retry.backoff(3, Duration.ofMillis(10))))
                .expectNext("ok al intento 3")
                .verifyComplete();
    }

    @Test
    void timeoutProducesError() {
        StepVerifier.create(Mono.never().timeout(Duration.ofMillis(50)))
                .verifyError(TimeoutException.class);
    }

    @Test
    void doFinallyAlwaysRuns() {
        AtomicInteger cleanups = new AtomicInteger();
        StepVerifier.create(Mono.error(new RuntimeException()).doFinally(signal -> cleanups.incrementAndGet()))
                .verifyError();
        StepVerifier.create(Mono.just(1).doFinally(signal -> cleanups.incrementAndGet()))
                .expectNext(1).verifyComplete();
        org.assertj.core.api.Assertions.assertThat(cleanups.get()).isEqualTo(2);
    }
}
