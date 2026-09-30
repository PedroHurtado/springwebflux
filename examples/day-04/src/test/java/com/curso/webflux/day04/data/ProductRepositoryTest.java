package com.curso.webflux.day04.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Limit;

import com.curso.webflux.day04.catalog.Product;
import com.curso.webflux.day04.catalog.ProductRepository;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * @DataR2dbcTest: "slice" de persistencia. Arranca SOLO R2DBC (H2 embebida + schema.sql/data.sql), los
 * repositorios de Spring Data y las transacciones; nada de web, controladores ni WebClient.
 *
 * Cuidado: a diferencia de @DataJpaTest, NO deshace los cambios al terminar cada test (el TestContext de
 * Spring no gestiona transacciones reactivas). Cada test usa sus propios productos para no depender del orden.
 */
@DataR2dbcTest
class ProductRepositoryTest {

    @Autowired
    ProductRepository repository;

    @Test
    void derivedQueries() {
        StepVerifier.create(repository.findByCategoryIgnoreCaseOrderById("PERIFERICOS").map(Product::id))
                .expectNext("1", "2")
                .verifyComplete();

        StepVerifier.create(repository.findAllByOrderByPriceDesc(Limit.of(2)).map(Product::id))
                .expectNext("4", "3")                 // portátil y monitor
                .verifyComplete();
    }

    @Test
    void newProductIsInsertedWithVersionZero() {
        var product = new Product(UUID.randomUUID().toString(), "Webcam", "video", new BigDecimal("39.90"), 12);

        StepVerifier.create(repository.save(product).flatMap(saved -> repository.findById(saved.id())))
                .assertNext(found -> {
                    assertThat(found.name()).isEqualTo("Webcam");
                    assertThat(found.version()).isZero();
                })
                .verifyComplete();
    }

    /** UPDATE ... WHERE stock >= :quantity: comprobar y descontar en una sola sentencia atómica. */
    @Test
    void reserveStockNeverLeavesNegativeStock() {
        StepVerifier.create(repository.reserveStock("4", 5)).expectNext(1).verifyComplete();   // quedaban 5
        StepVerifier.create(repository.reserveStock("4", 1)).expectNext(0).verifyComplete();   // ya no queda
        StepVerifier.create(repository.findById("4"))
                .assertNext(p -> {
                    assertThat(p.stock()).isZero();
                    assertThat(p.version()).isEqualTo(1);                                       // cambia el ETag
                })
                .verifyComplete();
    }

    /**
     * Bloqueo optimista con @Version: dos "peticiones" leen la misma versión del producto 5;
     * la primera guarda (versión 0 -> 1) y la segunda, que sigue teniendo la 0, falla en vez de pisarla.
     */
    @Test
    void staleVersionFailsWithOptimisticLocking() {
        Mono<Product> first = repository.findById("5");
        Mono<Product> second = repository.findById("5");

        StepVerifier.create(Mono.zip(first, second)
                        .flatMap(both -> repository.save(both.getT1().withPrice(new BigDecimal("55.00")))
                                .then(repository.save(both.getT2().withStock(0)))))
                .expectError(OptimisticLockingFailureException.class)
                .verify();

        StepVerifier.create(repository.findById("5"))
                .assertNext(p -> {
                    assertThat(p.price()).isEqualByComparingTo("55.00");   // ganó la primera
                    assertThat(p.stock()).isEqualTo(40);                   // la segunda no se aplicó
                })
                .verifyComplete();
    }
}
