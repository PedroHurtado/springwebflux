package com.curso.webflux.day02.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.curso.webflux.day02.catalog.ProductRepository;
import com.curso.webflux.day02.orders.DetailCheck.DetailError;
import com.curso.webflux.day02.orders.OrderRequest.DetailRequest;

import reactor.test.StepVerifier;

/**
 * Prueba unitaria del servicio, SIN Spring: se instancian las clases a mano.
 * Cada test parte de un catálogo nuevo (5 productos, ver ProductRepository).
 */
class OrderServiceTest {

    ProductRepository products;
    OrderService service;

    @BeforeEach
    void setUp() {
        products = new ProductRepository();
        service = new OrderService(products, new OrderRepository());
    }

    @Test
    void createsOrderCopyingProductDataAndDecreasesStock() {
        var request = new OrderRequest("cliente-1", List.of(
                new DetailRequest("1", 2),      // teclado 89.90
                new DetailRequest("3", 1)));    // monitor 249.00

        StepVerifier.create(service.create(request))
                .assertNext(order -> {
                    assertThat(order.id()).isNotNull();
                    assertThat(order.details()).extracting(OrderDetail::productName)
                            .containsExactly("Teclado mecánico", "Monitor 27 pulgadas");
                    assertThat(order.total()).isEqualByComparingTo("428.80");
                })
                .verifyComplete();

        // El stock se ha descontado
        StepVerifier.create(products.findById("1"))
                .assertNext(p -> assertThat(p.stock()).isEqualTo(23))
                .verifyComplete();
    }

    @Test
    void collectsEveryInvalidDetailInOneError() {
        var request = new OrderRequest("cliente-1", List.of(
                new DetailRequest("99", 1),     // no existe
                new DetailRequest("4", 50),     // solo hay 5
                new DetailRequest("2", 1)));    // correcta

        StepVerifier.create(service.create(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(OrderRejectedException.class);
                    List<DetailError> errors = errorsOf((OrderRejectedException) error);
                    assertThat(errors).extracting(DetailError::line).containsExactly(0, 1);
                    assertThat(errors.get(1).message()).contains("stock insuficiente");
                })
                .verify();

        // Nada se ha reservado: la línea correcta tampoco descuenta stock
        StepVerifier.create(products.findById("2"))
                .assertNext(p -> assertThat(p.stock()).isEqualTo(100))
                .verifyComplete();
    }

    @Test
    void failFastStopsAtTheFirstInvalidDetail() {
        var request = new OrderRequest("cliente-1", List.of(
                new DetailRequest("99", 1),
                new DetailRequest("4", 50)));

        StepVerifier.create(service.createFailFast(request))
                .expectErrorSatisfies(error -> assertThat(errorsOf((OrderRejectedException) error))
                        .extracting(DetailError::line).containsExactly(0))
                .verify();
    }

    /**
     * Tiempo virtual: cada consulta al catálogo tarda 50 ms (delayElement).
     * create() lanza las 5 consultas a la vez (flatMapSequential) -> termina a los 50 ms.
     */
    @Test
    void createQueriesTheCatalogConcurrently() {
        StepVerifier.withVirtualTime(() -> service.create(fiveDetails()))
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(49))
                .thenAwait(Duration.ofMillis(1))
                .expectNextCount(1)
                .expectComplete()
                .verify(Duration.ofSeconds(2));   // límite en tiempo REAL: con tiempo virtual, si el evento
                                                  // no llega, verifyComplete() se quedaría esperando siempre
    }

    /** createFailFast() consulta de una en una (concatMap) -> 5 x 50 ms = 250 ms. */
    @Test
    void createFailFastQueriesTheCatalogSequentially() {
        StepVerifier.withVirtualTime(() -> service.createFailFast(fiveDetails()))
                .expectSubscription()
                .expectNoEvent(Duration.ofMillis(249))
                .thenAwait(Duration.ofMillis(1))
                .expectNextCount(1)
                .expectComplete()
                .verify(Duration.ofSeconds(2));   // límite en tiempo REAL: con tiempo virtual, si el evento
                                                  // no llega, verifyComplete() se quedaría esperando siempre
    }

    @Test
    void unknownOrderIsAnOrderNotFoundError() {
        StepVerifier.create(service.findById("no-existe"))
                .expectError(OrderNotFoundException.class)
                .verify();
    }

    @Test
    void orderTotalIsTheSumOfSubtotals() {
        var product = new com.curso.webflux.day02.catalog.Product("x", "X", "c", new BigDecimal("1.50"), 10);
        Order order = Order.create("c", List.of(OrderDetail.of(product, 3), OrderDetail.of(product, 1)));
        assertThat(order.total()).isEqualByComparingTo("6.00");
    }

    private static OrderRequest fiveDetails() {
        return new OrderRequest("cliente-1", List.of(
                new DetailRequest("1", 1), new DetailRequest("2", 1), new DetailRequest("3", 1),
                new DetailRequest("4", 1), new DetailRequest("5", 1)));
    }

    @SuppressWarnings("unchecked")
    private static List<DetailError> errorsOf(OrderRejectedException ex) {
        return (List<DetailError>) ex.getBody().getProperties().get("errors");
    }
}
