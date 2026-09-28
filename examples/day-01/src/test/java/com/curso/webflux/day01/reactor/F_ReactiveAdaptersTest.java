package com.curso.webflux.day01.reactor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;

import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bloque 2 - "Bibliotecas reactivas" en Spring: ReactiveAdapterRegistry.
 *
 * Spring acepta como tipo de retorno de un controlador no solo Mono/Flux, sino
 * cualquier tipo registrado (CompletableFuture, RxJava, Mutiny, corrutinas de Kotlin...).
 * El registro traduce ese tipo a un Publisher de Reactive Streams.
 */
class F_ReactiveAdaptersTest {

    private final ReactiveAdapterRegistry registry = ReactiveAdapterRegistry.getSharedInstance();

    @Test
    void completableFutureIsAdaptedToPublisher() {
        ReactiveAdapter adapter = registry.getAdapter(CompletableFuture.class);
        assertThat(adapter).isNotNull();
        assertThat(adapter.getDescriptor().isMultiValue()).isFalse(); // se comporta como Mono

        Publisher<Object> publisher = adapter.toPublisher(CompletableFuture.completedFuture("hola"));
        StepVerifier.create(publisher).expectNext("hola").verifyComplete();
    }

    @Test
    void monoAndCompletableFutureInterop() {
        CompletableFuture<String> future = Mono.just("reactor").toFuture();
        StepVerifier.create(Mono.fromFuture(future)).expectNext("reactor").verifyComplete();
    }

    @Test
    void jdkFlowApiInterop() throws Exception {
        // java.util.concurrent.Flow (JDK 9+) es la misma especificación Reactive Streams
        try (SubmissionPublisher<String> jdkPublisher = new SubmissionPublisher<>()) {
            Flow.Publisher<String> flowPublisher = jdkPublisher;
            Flux<String> flux = JdkFlowAdapter.flowPublisherToFlux(flowPublisher);

            StepVerifier.create(flux.take(2))
                    .then(() -> List.of("uno", "dos").forEach(jdkPublisher::submit))
                    .expectNext("uno", "dos")
                    .verifyComplete();
        }
    }
}
