package com.curso.webflux.day04.catalog;

import java.math.BigDecimal;

import org.springframework.data.domain.Limit;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.reactive.ReactiveSortingRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Día 4: el repositorio en memoria de los días 1-3 se sustituye por un repositorio de Spring Data R2DBC.
 * Solo se DECLARA la interfaz: Spring Data genera la implementación al arrancar.
 *
 * Mismo contrato reactivo que antes (Mono/Flux), así que el resto de la aplicación apenas cambia:
 *   findById -> Mono vacío si no existe · findAll -> Flux que emite fila a fila según llegan de la BD.
 *
 * Tres formas de escribir consultas:
 *   1. Heredadas: findById, findAll(Sort), save, deleteById, existsById, count...
 *   2. Derivadas del nombre del método: findByCategoryIgnoreCaseOrderById.
 *   3. SQL explícito con @Query (y @Modifying para UPDATE/DELETE).
 */
public interface ProductRepository extends ReactiveCrudRepository<Product, String>,
        ReactiveSortingRepository<Product, String> {

    /** SELECT ... WHERE UPPER(category) = UPPER(:category) ORDER BY id */
    Flux<Product> findByCategoryIgnoreCaseOrderById(String category);

    /** SELECT ... ORDER BY price DESC LIMIT :limit (Limit: Spring Data 3.2+) */
    Flux<Product> findAllByOrderByPriceDesc(Limit limit);

    /** Un producto al azar para el ticker de precios: lo elige la BD, sin traer la tabla a memoria. */
    @Query("SELECT * FROM product ORDER BY RAND() LIMIT 1")
    Mono<Product> findRandom();

    /**
     * Reserva de stock ATÓMICA: comprobar y descontar en UNA sentencia.
     * Devuelve las filas modificadas: 1 = reservado, 0 = no había stock suficiente (o no existe).
     * Es lo que el día 2 quedaba pendiente: dos pedidos simultáneos ya no pueden dejar el stock en negativo.
     */
    @Modifying
    @Query("UPDATE product SET stock = stock - :quantity, version = version + 1 WHERE id = :id AND stock >= :quantity")
    Mono<Integer> reserveStock(String id, int quantity);

    /** Cambio de precio del ticker: solo toca el precio (y la versión, para que cambie el ETag). */
    @Modifying
    @Query("UPDATE product SET price = :price, version = version + 1 WHERE id = :id")
    Mono<Integer> updatePrice(String id, BigDecimal price);
}
