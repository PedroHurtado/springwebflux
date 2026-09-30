package com.curso.webflux.day04.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/**
 * BlockHound detecta llamadas BLOQUEANTES (Thread.sleep, E/S de ficheros o sockets, wait, locks...) hechas
 * desde hilos que NO deben bloquear: parallel, single y los event loops de Netty. Lanza BlockingOperationError.
 *
 * Reactor ya impide block() en esos hilos; BlockHound va más allá y encuentra el bloqueo ESCONDIDO dentro
 * de una biblioteca (un driver JDBC, un SDK síncrono, un Files.readAllBytes...).
 *
 * Se instala una vez para toda la JVM y no se puede desinstalar: por eso estos tests llevan @Tag("blockhound")
 * y el pom.xml los ejecuta en una JVM aparte, con -XX:+AllowRedefinitionToAddDeleteMethods (Java 13+).
 *   ./mvnw test                                  -> las dos tandas (la normal y esta)
 *   ./mvnw test-compile surefire:test@blockhound   -> solo esta
 */
@Tag("blockhound")
class BlockHoundTest {

    @BeforeAll
    static void install() {
        BlockHound.install();
    }

    /** Un Thread.sleep "escondido" en un hilo parallel: BlockHound lo convierte en un error. */
    @Test
    void blockingCallOnANonBlockingThreadIsDetected() {
        Mono<String> hiddenBlockingCall = Mono.fromCallable(BlockHoundTest::legacySynchronousApi)
                .subscribeOn(Schedulers.parallel());

        StepVerifier.create(hiddenBlockingCall)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(BlockingOperationError.class)
                        .hasMessageContaining("java.lang.Thread.sleep"))
                .verify(Duration.ofSeconds(2));
    }

    /** La solución correcta: llevar la llamada bloqueante a boundedElastic, pensado para eso. */
    @Test
    void boundedElasticIsAllowedToBlock() {
        Mono<String> isolated = Mono.fromCallable(BlockHoundTest::legacySynchronousApi)
                .subscribeOn(Schedulers.boundedElastic());

        StepVerifier.create(isolated)
                .expectNext("respuesta lenta")
                .verifyComplete();
    }

    /** Operadores con tiempo (delayElement) usan parallel pero NO bloquean: BlockHound no protesta. */
    @Test
    void nonBlockingDelaysAreFine() {
        StepVerifier.create(Mono.just("ok").delayElement(Duration.ofMillis(50)))
                .expectNext("ok")
                .verifyComplete();
    }

    /** Simula una API síncrona de terceros (un SDK, un driver JDBC...). */
    private static String legacySynchronousApi() throws InterruptedException {
        Thread.sleep(20);
        return "respuesta lenta";
    }
}
