package com.curso.webflux.day01.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Lógica de negocio compuesta con operadores de Reactor.
 *
 * Regla de oro: el servicio devuelve Mono/Flux y NUNCA llama a block() ni subscribe().
 * Quien se suscribe es el framework, cuando el DispatcherHandler escribe la respuesta.
 */
@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    public Flux<Product> findAll(String category) {
        return category == null ? repository.findAll() : repository.findByCategory(category);
    }

    public Mono<Product> findById(String id) {
        return repository.findById(id)
                // Mono vacío -> error de negocio (Supplier: la excepción se crea solo si hace falta)
                .switchIfEmpty(Mono.error(() -> new ProductNotFoundException(id)));
    }

    public Mono<Product> create(ProductRequest request) {
        return repository.save(request.toProduct(null));
    }

    public Mono<Product> update(String id, ProductRequest request) {
        return findById(id)
                // flatMap: la transformación devuelve otro Publisher (operación asíncrona)
                .flatMap(existing -> repository.save(request.toProduct(existing.id())));
    }

    public Mono<Void> delete(String id) {
        return repository.deleteById(id)
                .flatMap(deleted -> deleted ? Mono.<Void>empty() : Mono.error(new ProductNotFoundException(id)));
    }

    /**
     * Flujo infinito de cambios de precio: un evento por segundo.
     * Se consume como Server-Sent Events (text/event-stream).
     */
    public Flux<PriceChange> priceTicker() {
        return Flux.interval(Duration.ofSeconds(1))
                .concatMap(tick -> repository.findAll().collectList())
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(ThreadLocalRandom.current().nextInt(list.size())))
                .concatMap(this::randomPriceChange);
    }

    private Mono<PriceChange> randomPriceChange(Product product) {
        double factor = 1 + ThreadLocalRandom.current().nextDouble(-0.05, 0.05);
        BigDecimal newPrice = product.price().multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_UP);
        return repository.save(product.withPrice(newPrice))
                .map(saved -> new PriceChange(saved.id(), saved.name(), product.price(), newPrice));
    }

    public record PriceChange(String productId, String name, BigDecimal oldPrice, BigDecimal newPrice) {
    }
}
