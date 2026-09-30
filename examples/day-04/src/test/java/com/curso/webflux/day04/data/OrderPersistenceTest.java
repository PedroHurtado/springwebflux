package com.curso.webflux.day04.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;

import com.curso.webflux.day04.catalog.Product;
import com.curso.webflux.day04.catalog.ProductRepository;
import com.curso.webflux.day04.orders.OrderRejectedException;
import com.curso.webflux.day04.orders.OrderRepository;
import com.curso.webflux.day04.orders.OrderRequest;
import com.curso.webflux.day04.orders.OrderRequest.DetailRequest;
import com.curso.webflux.day04.orders.OrderService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * El servicio de pedidos contra la base de datos REAL (H2): agregado en dos tablas, transacción
 * reactiva con rollback y reserva de stock concurrente.
 *
 * @DataR2dbcTest solo registra repositorios de Spring Data: OrderRepository (DatabaseClient) y
 * OrderService se añaden con @Import. El TransactionalOperator sí lo crea el slice.
 */
@DataR2dbcTest
@Import({ OrderRepository.class, OrderService.class })
class OrderPersistenceTest {

    @Autowired
    OrderService service;

    @Autowired
    OrderRepository orders;

    @Autowired
    ProductRepository products;

    @Test
    void savesAndReloadsTheWholeAggregate() {
        var request = new OrderRequest("cliente-bd", List.of(new DetailRequest("1", 2), new DetailRequest("3", 1)));

        StepVerifier.create(service.create(request)
                        .flatMap(created -> orders.findById(created.id()).map(reloaded -> List.of(created, reloaded))))
                .assertNext(both -> assertThat(both.get(1)).isEqualTo(both.get(0)))   // mismo record, campo a campo
                .verifyComplete();

        StepVerifier.create(orders.findByCustomer("cliente-bd"))
                .assertNext(order -> assertThat(order.details()).hasSize(2))
                .verifyComplete();
    }

    /**
     * El INSERT de la cabecera falla (customer_id admite 60 caracteres) DESPUÉS de reservar el stock
     * (DataAccessException: Spring traduce los errores de R2DBC a su jerarquía de excepciones):
     * la transacción se deshace y el stock vuelve a estar como antes.
     */
    @Test
    void rollsBackTheStockReservationWhenSavingFails() {
        var request = new OrderRequest("x".repeat(100), List.of(new DetailRequest("2", 10)));

        StepVerifier.create(stockOf("2").flatMap(before -> service.create(request)
                        .then(Mono.<Integer>empty())
                        .onErrorResume(DataAccessException.class, e -> stockOf("2"))
                        .map(after -> List.of(before, after))))
                .assertNext(stock -> assertThat(stock.get(1)).isEqualTo(stock.get(0)))
                .verifyComplete();
    }

    /**
     * 10 pedidos simultáneos de 1 unidad del producto 4 (quedan 5): todos pasan la validación, pero el
     * UPDATE condicional solo deja reservar 5. El resto se rechaza (422) y el stock nunca es negativo.
     */
    @Test
    void concurrentOrdersNeverOversell() {
        var request = new OrderRequest("cliente-concurrente", List.of(new DetailRequest("4", 1)));

        Flux<String> results = Flux.range(0, 10)
                .flatMap(i -> service.create(request)
                        .map(order -> "creado")
                        .onErrorResume(OrderRejectedException.class, e -> Mono.just("rechazado")));

        StepVerifier.create(results.filter("creado"::equals).count())
                .expectNext(5L)
                .verifyComplete();
        StepVerifier.create(stockOf("4")).expectNext(0).verifyComplete();
    }

    private Mono<Integer> stockOf(String productId) {
        return products.findById(productId).map(Product::stock);
    }
}
