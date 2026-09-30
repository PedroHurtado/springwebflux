package com.curso.webflux.day04.bff;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.curso.webflux.day04.client.OrderDto;
import com.curso.webflux.day04.client.OrderDto.LineDto;
import com.curso.webflux.day04.client.ProductDto;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/** El acumulador del BFF, sin Spring ni HTTP: mismo enfoque que OrderValidationTest del día 2. */
class OrderSummaryTest {

    private static final LineDto KEYBOARD = new LineDto("p1", "Teclado", new BigDecimal("90.00"), 1);
    private static final LineDto MOUSE = new LineDto("p2", "Ratón", new BigDecimal("20.00"), 2);
    private static final OrderDto ORDER = new OrderDto("o-1", "c-1", Instant.parse("2026-09-30T08:00:00Z"),
            List.of(KEYBOARD, MOUSE), new BigDecimal("130.00"));

    @Test
    void accumulatesLinesAndCurrentTotalWithoutMutatingTheSeed() {
        OrderSummary seed = OrderSummary.start(ORDER);

        StepVerifier.create(Flux.just(
                        LineView.of(KEYBOARD, new ProductDto("p1", "Teclado", new BigDecimal("100.00"), 10)),
                        LineView.of(MOUSE, new ProductDto("p2", "Ratón", new BigDecimal("25.00"), 1)))
                        .reduce(seed, OrderSummary::add))
                .assertNext(summary -> {
                    assertThat(summary.lines()).extracting(LineView::status)
                            .containsExactly(LineView.Status.AVAILABLE, LineView.Status.OUT_OF_STOCK);
                    assertThat(summary.totalAtPurchase()).isEqualByComparingTo("130.00");
                    assertThat(summary.totalAtCurrentPrices()).isEqualByComparingTo("150.00");   // 100 + 2 x 25
                    assertThat(summary.complete()).isTrue();
                })
                .verifyComplete();

        assertThat(seed.lines()).isEmpty();                          // inmutable: la semilla no ha cambiado
    }

    @Test
    void unknownLineMakesTheSummaryIncomplete() {
        OrderSummary summary = OrderSummary.start(ORDER)
                .add(LineView.discontinued(KEYBOARD))
                .add(LineView.unknown(MOUSE));

        assertThat(summary.complete()).isFalse();
        assertThat(summary.totalAtCurrentPrices()).isEqualByComparingTo("0");
        assertThat(summary.lines()).extracting(LineView::currentPrice).containsOnlyNulls();
    }
}
