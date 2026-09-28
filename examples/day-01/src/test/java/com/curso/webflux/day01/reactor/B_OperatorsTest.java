package com.curso.webflux.day01.reactor;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Bloque 2 - Operadores más usados en aplicaciones WebFlux.
 * Guía oficial "Which operator do I need?":
 * https://projectreactor.io/docs/core/release/reference/apdx-operatorChoice.html
 */
class B_OperatorsTest {

    private Mono<String> findUserName(int id) {
        return Mono.just("user-" + id).delayElement(Duration.ofMillis(50L * (4 - id)));
    }

    @Test
    void mapIsSynchronousTransformation() {
        StepVerifier.create(Flux.just("webflux", "reactor").map(String::toUpperCase))
                .expectNext("WEBFLUX", "REACTOR")
                .verifyComplete();
    }

    @Test
    void flatMapIsAsyncAndDoesNotPreserveOrder() {
        // flatMap se suscribe a los 3 Mono a la vez: el orden depende de cuál termina antes
        StepVerifier.create(Flux.just(1, 2, 3).flatMap(this::findUserName).collectList())
                .expectNext(List.of("user-3", "user-2", "user-1"))
                .verifyComplete();
    }

    @Test
    void concatMapPreservesOrderButIsSequential() {
        StepVerifier.create(Flux.just(1, 2, 3).concatMap(this::findUserName))
                .expectNext("user-1", "user-2", "user-3")
                .verifyComplete();
    }

    @Test
    void flatMapSequentialIsConcurrentAndOrdered() {
        StepVerifier.create(Flux.just(1, 2, 3).flatMapSequential(this::findUserName))
                .expectNext("user-1", "user-2", "user-3")
                .verifyComplete();
    }

    @Test
    void filterTakeSkip() {
        StepVerifier.create(Flux.range(1, 20).filter(n -> n % 2 == 0).skip(1).take(3))
                .expectNext(4, 6, 8)
                .verifyComplete();
    }

    @Test
    void zipCombinesInParallel() {
        Mono<String> name = Mono.just("Portátil").delayElement(Duration.ofMillis(50));
        Mono<Integer> stock = Mono.just(5).delayElement(Duration.ofMillis(50));

        // Ambas "consultas" se lanzan a la vez; el resultado llega cuando terminan las dos
        StepVerifier.create(Mono.zip(name, stock).map(t -> t.getT1() + " x" + t.getT2()))
                .expectNext("Portátil x5")
                .verifyComplete();
    }

    @Test
    void mergeVsConcat() {
        Flux<String> slow = Flux.just("s1", "s2").delayElements(Duration.ofMillis(30));
        Flux<String> fast = Flux.just("f1", "f2").delayElements(Duration.ofMillis(10));

        StepVerifier.create(Flux.concat(slow, fast)).expectNext("s1", "s2", "f1", "f2").verifyComplete();
        StepVerifier.create(Flux.merge(slow, fast)).expectNext("f1", "f2", "s1", "s2").verifyComplete();
    }

    @Test
    void defaultIfEmptyAndSwitchIfEmpty() {
        StepVerifier.create(Mono.<String>empty().defaultIfEmpty("por defecto"))
                .expectNext("por defecto").verifyComplete();

        StepVerifier.create(Mono.empty().switchIfEmpty(Mono.error(new IllegalStateException("no encontrado"))))
                .verifyErrorMessage("no encontrado");
    }

    @Test
    void collectAndReduce() {
        StepVerifier.create(Flux.range(1, 4).reduce(0, Integer::sum)).expectNext(10).verifyComplete();
        StepVerifier.create(Flux.just("a", "b").collectList()).expectNext(List.of("a", "b")).verifyComplete();
    }

    @Test
    void sideEffectsWithDoOn() {
        StepVerifier.create(Flux.just(1, 2)
                        .doOnSubscribe(s -> System.out.println("suscrito"))
                        .doOnNext(n -> System.out.println("elemento " + n))
                        .doOnComplete(() -> System.out.println("completado"))
                        .log()) // imprime todas las señales Reactive Streams (onSubscribe, request, onNext...)
                .expectNext(1, 2)
                .verifyComplete();
    }
}
