package com.curso.webflux.day01.catalog;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio en memoria con API reactiva.
 *
 * Simula la latencia de una base de datos con delayElement/delayElements,
 * que NO bloquean el hilo: programan la emisión en un scheduler de Reactor.
 * En días posteriores se puede sustituir por Spring Data R2DBC sin tocar el controlador.
 */
@Repository
public class ProductRepository {

    private static final Duration LATENCY = Duration.ofMillis(50);

    private final Map<String, Product> store = new ConcurrentHashMap<>();

    public ProductRepository() {
        List.of(
                new Product("1", "Teclado mecánico", "perifericos", new BigDecimal("89.90"), 25),
                new Product("2", "Ratón inalámbrico", "perifericos", new BigDecimal("29.95"), 100),
                new Product("3", "Monitor 27 pulgadas", "monitores", new BigDecimal("249.00"), 10),
                new Product("4", "Portátil 14 pulgadas", "portatiles", new BigDecimal("1099.00"), 5),
                new Product("5", "Auriculares", "audio", new BigDecimal("59.00"), 40))
            .forEach(p -> store.put(p.id(), p));
    }

    public Flux<Product> findAll() {
        // Flux.defer: la "consulta" se evalúa en cada suscripción (publisher frío)
        return Flux.defer(() -> Flux.fromIterable(store.values()))
                .sort(Comparator.comparing(Product::id))
                .delayElements(Duration.ofMillis(10));
    }

    public Flux<Product> findByCategory(String category) {
        return findAll().filter(p -> p.category().equalsIgnoreCase(category));
    }

    public Mono<Product> findById(String id) {
        // Mono.justOrEmpty: si no existe, Mono vacío (nunca null)
        return Mono.defer(() -> Mono.justOrEmpty(store.get(id))).delayElement(LATENCY);
    }

    public Mono<Product> save(Product product) {
        return Mono.fromSupplier(() -> {
            Product toSave = product.id() == null ? product.withId(UUID.randomUUID().toString()) : product;
            store.put(toSave.id(), toSave);
            return toSave;
        });
    }

    public Mono<Boolean> deleteById(String id) {
        return Mono.fromSupplier(() -> store.remove(id) != null);
    }
}
