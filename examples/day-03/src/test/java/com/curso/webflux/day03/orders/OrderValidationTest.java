package com.curso.webflux.day03.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.curso.webflux.day03.catalog.Product;
import com.curso.webflux.day03.orders.DetailCheck.DetailError;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * El acumulador es una función pura: se prueba sin Spring, sin E/S y sin repositorios.
 */
class OrderValidationTest {

    static final Product KEYBOARD = new Product("1", "Teclado", "perifericos", new BigDecimal("10.00"), 5);

    static DetailCheck valid(int quantity) {
        return new DetailCheck.Valid(OrderDetail.of(KEYBOARD, quantity));
    }

    static DetailCheck invalid(int line) {
        return new DetailCheck.Invalid(new DetailError(line, "99", "el producto no existe"));
    }

    @Test
    void addNeverModifiesTheCurrentAccumulator() {
        OrderValidation empty = OrderValidation.empty();
        OrderValidation one = empty.add(valid(1));

        assertThat(empty.details()).isEmpty();          // la semilla sigue vacía: se puede compartir
        assertThat(one.details()).hasSize(1);
    }

    @Test
    void separatesValidDetailsFromErrorsKeepingTheOrder() {
        OrderValidation result = OrderValidation.empty()
                .add(invalid(0))
                .add(valid(2))
                .add(invalid(2));

        assertThat(result.isValid()).isFalse();
        assertThat(result.details()).hasSize(1);
        assertThat(result.errors()).extracting(DetailError::line).containsExactly(0, 2);
    }

    /** Lo mismo que hace OrderService.create, pero con un Flux fijo en lugar de consultas al catálogo. */
    @Test
    void reduceOverAFluxRejectsWithEveryError() {
        var decision = Flux.just(invalid(0), valid(1), invalid(2))
                .reduce(OrderValidation.empty(), OrderValidation::add)
                .flatMap(OrderValidation::toDetailsOrReject);

        StepVerifier.create(decision)
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(OrderRejectedException.class);
                    List<?> errors = (List<?>) ((OrderRejectedException) error).getBody().getProperties().get("errors");
                    assertThat(errors).hasSize(2);
                })
                .verify();
    }

    @Test
    void reduceOverAFluxOfValidDetailsEmitsTheDetails() {
        var decision = Flux.just(valid(1), valid(2))
                .reduce(OrderValidation.empty(), OrderValidation::add)
                .flatMap(OrderValidation::toDetailsOrReject);

        StepVerifier.create(decision)
                .assertNext(details -> assertThat(details).extracting(OrderDetail::quantity).containsExactly(1, 2))
                .verifyComplete();
    }
}
