package com.curso.webflux.day04.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Lógica de negocio compuesta con operadores de Reactor.
 *
 * Regla de oro: el servicio devuelve Mono/Flux y NUNCA llama a block() ni subscribe().
 * Quien se suscribe es el framework, cuando el DispatcherHandler escribe la respuesta.
 *
 * Día 4: el repositorio es de Spring Data R2DBC (H2). La firma de los métodos no cambia: por eso el
 * cambio de "memoria" a "base de datos" no afecta a controladores, vistas, WebSockets ni al BFF.
 */
@Service
public class ProductService {

    private static final Sort BY_ID = Sort.by("id");

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public Flux<Product> findAll(String category) {
        return category == null ? repository.findAll(BY_ID) : repository.findByCategoryIgnoreCaseOrderById(category);
    }

    /** Buscador con filtros opcionales: cada filtro es un paso más del pipeline. */
    public Flux<Product> search(ProductSearch search) {
        return findAll(search.category())
                .filter(p -> search.maxPrice() == null || p.price().compareTo(search.maxPrice()) <= 0)
                .sort(search.sortOrDefault().comparator());
    }

    /** Día 4: ORDER BY + LIMIT en la base de datos (antes: sort + take en memoria). */
    public Flux<Product> top(int limit) {
        return repository.findAllByOrderByPriceDesc(Limit.of(limit));
    }

    public Mono<Product> findById(String id) {
        return repository.findById(id)
                // Mono vacío -> error de negocio (Supplier: la excepción se crea solo si hace falta)
                .switchIfEmpty(Mono.error(() -> new ProductNotFoundException(id)));
    }

    /** El id lo asigna la aplicación; version null -> Spring Data hace INSERT (y fija version = 0). */
    public Mono<Product> create(ProductRequest request) {
        return repository.save(request.toProduct(UUID.randomUUID().toString()));
    }

    /**
     * Se conserva la versión LEÍDA: el UPDATE lleva "WHERE version = ?". Si otra petición modificó el
     * producto entre la lectura y la escritura, falla con OptimisticLockingFailureException (409, ver
     * GlobalExceptionHandler) en lugar de pisar el cambio del otro.
     */
    public Mono<Product> update(String id, ProductRequest request) {
        return findById(id)
                // flatMap: la transformación devuelve otro Publisher (operación asíncrona)
                .flatMap(existing -> repository.save(request.toProduct(existing.id()).withVersion(existing.version())));
    }

    public Mono<Void> delete(String id) {
        return findById(id)                                   // 404 si no existe
                .flatMap(repository::delete);
    }

    /**
     * Flujo infinito de cambios de precio: un evento por segundo. Es un publisher FRÍO: cada suscriptor
     * tendría su propio ticker. Para compartir uno solo entre todos los clientes (SSE y WebSocket) se
     * publica "en caliente" en PriceFeed.
     */
    public Flux<PriceChange> priceTicker() {
        return Flux.interval(Duration.ofSeconds(1))
                .concatMap(tick -> repository.findRandom())   // día 4: lo elige la BD (antes: collectList)
                .concatMap(this::randomPriceChange);
    }

    private Mono<PriceChange> randomPriceChange(Product product) {
        double factor = 1 + ThreadLocalRandom.current().nextDouble(-0.05, 0.05);
        BigDecimal newPrice = product.price().multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_UP);
        // UPDATE que solo cambia el precio: respeta el stock que hayan reservado los pedidos
        return repository.updatePrice(product.id(), newPrice)
                .filter(updated -> updated == 1)              // borrado entretanto: no hay evento
                .map(updated -> new PriceChange(product.id(), product.name(), product.price(), newPrice));
    }

    public record PriceChange(String productId, String name, BigDecimal oldPrice, BigDecimal newPrice) {
    }
}
