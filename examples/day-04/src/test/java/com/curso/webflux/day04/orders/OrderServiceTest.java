package com.curso.webflux.day04.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.curso.webflux.day04.catalog.Product;
import com.curso.webflux.day04.catalog.ProductRepository;
import com.curso.webflux.day04.orders.DetailCheck.DetailError;
import com.curso.webflux.day04.orders.OrderRequest.DetailRequest;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.PublisherProbe;

/**
 * Prueba UNITARIA del servicio: sin Spring y sin base de datos.
 *
 * Día 4: el repositorio ya no es una clase en memoria sino una interfaz de Spring Data R2DBC, así que se
 * sustituye por un DOBLE de Mockito. Lo importante al simular un repositorio reactivo:
 *   - el doble devuelve Mono/Flux (nunca null);
 *   - con thenAnswer el Mono se crea en CADA llamada (como haría el repositorio real), así que la latencia
 *     simulada (delayElement) se monta dentro de StepVerifier.withVirtualTime y usa el reloj virtual;
 *   - PublisherProbe distingue "se llamó al método" de "se SUSCRIBIÓ al Mono" (que es cuando se ejecuta).
 * La persistencia real (SQL, transacción, rollback) se prueba en data/OrderPersistenceTest.
 */
class OrderServiceTest {

    static final Duration LATENCY = Duration.ofMillis(50);

    static final Map<String, Product> CATALOG = Map.of(
            "1", new Product("1", "Teclado mecánico", "perifericos", new BigDecimal("89.90"), 25, 0L),
            "2", new Product("2", "Ratón inalámbrico", "perifericos", new BigDecimal("29.95"), 100, 0L),
            "3", new Product("3", "Monitor 27 pulgadas", "monitores", new BigDecimal("249.00"), 10, 0L),
            "4", new Product("4", "Portátil 14 pulgadas", "portatiles", new BigDecimal("1099.00"), 5, 0L),
            "5", new Product("5", "Auriculares", "audio", new BigDecimal("59.00"), 40, 0L));

    ProductRepository products;
    OrderRepository orders;
    TransactionalOperator transactions;
    PublisherProbe<Order> save;
    OrderService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        orders = mock(OrderRepository.class);
        transactions = mock(TransactionalOperator.class);

        // Cada consulta al "catálogo" tarda 50 ms, sin bloquear
        when(products.findById(anyString()))
                .thenAnswer(call -> Mono.justOrEmpty(CATALOG.get(call.getArgument(0, String.class))).delayElement(LATENCY));
        when(products.reserveStock(anyString(), anyInt())).thenReturn(Mono.just(1));
        // El Mono que devuelve save() es una sonda: después se puede preguntar si alguien se suscribió
        save = PublisherProbe.of(Mono.just(new Order("o-1", "cliente-1", null, List.of(), BigDecimal.ZERO)));
        when(orders.save(any())).thenReturn(save.mono());
        // Sin BD no hay transacción: el operador devuelve el mismo pipeline
        when(transactions.transactional(any(Mono.class))).thenAnswer(call -> call.getArgument(0));

        service = new OrderService(products, orders, transactions);
    }

    @Test
    void createsOrderReservingStockInsideATransaction() {
        var request = new OrderRequest("cliente-1", List.of(
                new DetailRequest("1", 2),      // teclado 89.90
                new DetailRequest("3", 1)));    // monitor 249.00

        StepVerifier.create(service.create(request))
                .assertNext(order -> assertThat(order.id()).isEqualTo("o-1"))
                .verifyComplete();

        verify(orders).save(argThat(order -> order.total().compareTo(new BigDecimal("428.80")) == 0
                && order.details().get(0).productName().equals("Teclado mecánico")));
        verify(products).reserveStock("1", 2);
        verify(products).reserveStock("3", 1);
        verify(transactions).transactional(any(Mono.class));
        save.assertWasSubscribed();
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

        // Nada se ha reservado ni guardado: la línea correcta tampoco
        verify(products, never()).reserveStock(anyString(), anyInt());
        save.assertWasNotSubscribed();
    }

    /**
     * La validación dio el pedido por bueno, pero al reservar, otro pedido se había llevado el stock
     * (el UPDATE condicional devuelve 0 filas). Resultado: 422 y el pedido NO se guarda.
     *
     * Ojo al detalle: orders.save(...) SÍ se ha llamado (then(orders.save(order)) lo invoca al montar el
     * pipeline), pero su Mono nunca se suscribió, así que no se ejecutó ningún INSERT. Con verify() de
     * Mockito no se ve la diferencia; con PublisherProbe, sí.
     */
    @Test
    void rejectsTheOrderWhenAnotherOneTookTheStock() {
        when(products.reserveStock("3", 1)).thenReturn(Mono.just(0));
        var request = new OrderRequest("cliente-1", List.of(new DetailRequest("1", 1), new DetailRequest("3", 1)));

        StepVerifier.create(service.create(request))
                .expectErrorSatisfies(error -> assertThat(errorsOf((OrderRejectedException) error))
                        .singleElement()
                        .satisfies(e -> assertThat(e.message()).contains("otro pedido")))
                .verify();

        verify(orders).save(any());          // se llamó al método...
        save.assertWasNotSubscribed();       // ...pero no se ejecutó
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

        verify(products, never()).findById("4");   // concatMap: la segunda línea ni se consultó
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
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void unknownOrderIsAnOrderNotFoundError() {
        when(orders.findById("no-existe")).thenReturn(Mono.empty());

        StepVerifier.create(service.findById("no-existe"))
                .expectError(OrderNotFoundException.class)
                .verify();
    }

    @Test
    void orderTotalIsTheSumOfSubtotals() {
        var product = new Product("x", "X", "c", new BigDecimal("1.50"), 10);
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
