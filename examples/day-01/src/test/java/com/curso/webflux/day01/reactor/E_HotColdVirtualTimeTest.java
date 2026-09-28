package com.curso.webflux.day01.reactor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bloque 2 - Publishers fríos vs calientes y tiempo virtual.
 */
class E_HotColdVirtualTimeTest {

    @Test
    void coldPublisherReplaysForEachSubscriber() {
        Flux<Integer> cold = Flux.range(1, 3);
        List<String> seen = new ArrayList<>();

        cold.subscribe(n -> seen.add("A" + n));
        cold.subscribe(n -> seen.add("B" + n));

        assertThat(seen).containsExactly("A1", "A2", "A3", "B1", "B2", "B3");
    }

    @Test
    void hotPublisherWithSinksOnlyEmitsToCurrentSubscribers() {
        Sinks.Many<String> sink = Sinks.many().multicast().directBestEffort();
        Flux<String> hot = sink.asFlux();
        List<String> seen = new ArrayList<>();

        hot.subscribe(v -> seen.add("A:" + v));
        sink.tryEmitNext("evento-1");
        hot.subscribe(v -> seen.add("B:" + v)); // B llega tarde: no ve evento-1
        sink.tryEmitNext("evento-2");

        assertThat(seen).containsExactly("A:evento-1", "A:evento-2", "B:evento-2");
    }

    @Test
    void shareTurnsColdIntoHot() {
        Flux<Long> shared = Flux.interval(Duration.ofMillis(10)).take(5).share();
        StepVerifier.create(shared).expectNextCount(5).verifyComplete();
    }

    @Test
    void virtualTimeAvoidsRealWaiting() {
        // Una hora de "ticks" verificada en milisegundos
        StepVerifier.withVirtualTime(() -> Flux.interval(Duration.ofMinutes(10)).take(6))
                .expectSubscription()
                .thenAwait(Duration.ofHours(1))
                .expectNextCount(6)
                .verifyComplete();
    }
}
