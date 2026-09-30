package com.curso.webflux.day04.reactor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.curso.webflux.day04.client.CorrelationId;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.test.StepVerifierOptions;
import reactor.util.context.Context;

/**
 * El Context de Reactor (el día 3 lo usamos para X-Request-Id y es donde Spring Security guarda el usuario
 * y donde vive la transacción de R2DBC). Reglas que conviene comprobar con tests:
 *   1. El Context viaja del suscriptor HACIA ARRIBA: contextWrite solo lo ven los operadores anteriores.
 *   2. Se lee con deferContextual / transformDeferredContextual, no con un ThreadLocal.
 *   3. No depende del hilo: sigue ahí aunque el pipeline cambie de hilo (publishOn / subscribeOn).
 */
class ContextTest {

    /** Lo que haría un servicio: leer el id de correlación del Context de la suscripción. */
    static Mono<String> greeting() {
        return Mono.deferContextual(context -> Mono.just("petición " + CorrelationId.from(context).orElse("sin id")));
    }

    @Test
    void contextWriteIsVisibleOnlyUpstream() {
        Mono<String> pipeline = greeting()                                // lee el Context
                .contextWrite(Context.of(CorrelationId.CONTEXT_KEY, "abc"))   // lo escribe (debajo: lo ve greeting)
                .flatMap(text -> greeting().map(other -> text + " / " + other))   // debajo de contextWrite: no lo ve
                ;

        StepVerifier.create(pipeline)
                .expectNext("petición abc / petición sin id")
                .verifyComplete();
    }

    /** Es lo que hace CorrelationIdWebFilter: contextWrite sobre TODO lo que devuelve la cadena. */
    @Test
    void theContextSurvivesThreadHops() {
        Mono<String> pipeline = Mono.just("x")
                .publishOn(Schedulers.parallel())                          // cambia de hilo
                .flatMap(ignored -> greeting())
                .subscribeOn(Schedulers.boundedElastic())                   // y otro
                .contextWrite(Context.of(CorrelationId.CONTEXT_KEY, "req-42"));

        StepVerifier.create(pipeline)
                .expectNext("petición req-42")
                .verifyComplete();
    }

    /**
     * StepVerifier puede SER el suscriptor que aporta el Context (como hace WebFlux en una petición real)
     * y comprobar el Context que llega a la suscripción.
     */
    @Test
    void stepVerifierCanProvideAndInspectTheContext() {
        var options = StepVerifierOptions.create()
                .withInitialContext(Context.of(CorrelationId.CONTEXT_KEY, "desde-el-test"));

        StepVerifier.create(greeting(), options)
                .expectAccessibleContext()
                .assertThat(context -> assertThat(CorrelationId.from(context)).hasValue("desde-el-test"))
                .then()
                .expectNext("petición desde-el-test")
                .verifyComplete();
    }
}
