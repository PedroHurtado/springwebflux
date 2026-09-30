package com.curso.webflux.day04.ws;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.curso.webflux.day04.catalog.ProductService.PriceChange;

import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;

/**
 * La lógica del WebSocket de precios SIN WebSocket ni servidor: dos TestPublisher hacen de "cliente que
 * envía comandos" y de "ticker de precios", y el test decide QUÉ se emite y CUÁNDO.
 *
 * TestPublisher además registra las suscripciones: se puede comprobar que switchMap cancela la
 * suscripción anterior al feed y que, al cancelar, no queda nadie suscrito (sin fugas).
 */
class PriceWatchTest {

    final TestPublisher<Set<String>> commands = TestPublisher.create();
    final TestPublisher<PriceChange> changes = TestPublisher.create();

    @Test
    void sendsOnlyTheWatchedProductsAndSwitchesWithEachCommand() {
        StepVerifier.create(PriceWebSocketHandler.watch(commands.flux(), changes.flux()))
                .then(() -> changes.assertNoSubscribers())          // sin comando no se escucha el feed
                .then(() -> commands.next(Set.of("1")))
                .then(() -> changes.next(change("2"), change("1")))
                .expectNext(change("1"))                             // el 2 se filtra
                .then(() -> commands.next(Set.of("2")))              // nuevo comando: switchMap
                .then(() -> changes.assertSubscribers(1))            // la suscripción anterior se canceló
                .then(() -> changes.next(change("1"), change("2")))
                .expectNext(change("2"))
                .then(() -> commands.next(Set.of()))                 // vacío = todos
                .then(() -> changes.next(change("3"), change("4")))
                .expectNext(change("3"), change("4"))
                .thenCancel()                                        // el cliente cierra
                .verify();

        changes.assertNoSubscribers();
        commands.assertCancelled();
    }

    private static PriceChange change(String productId) {
        return new PriceChange(productId, "Producto " + productId, BigDecimal.ONE, BigDecimal.TEN);
    }
}
