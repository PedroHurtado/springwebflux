package com.curso.webflux.day02.orders;

import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Repositorio de pedidos en memoria con API reactiva (mismo estilo que ProductRepository). */
@Repository
public class OrderRepository {

    private final Map<String, Order> store = new ConcurrentHashMap<>();

    public Flux<Order> findAll() {
        return Flux.defer(() -> Flux.fromIterable(store.values()))
                .sort(Comparator.comparing(Order::createdAt));
    }

    public Flux<Order> findByCustomer(String customerId) {
        return findAll().filter(order -> order.customerId().equals(customerId));
    }

    public Mono<Order> findById(String id) {
        return Mono.defer(() -> Mono.justOrEmpty(store.get(id))).delayElement(Duration.ofMillis(20));
    }

    public Mono<Order> save(Order order) {
        return Mono.fromSupplier(() -> {
            Order toSave = order.id() == null ? order.withId(UUID.randomUUID().toString()) : order;
            store.put(toSave.id(), toSave);
            return toSave;
        });
    }
}
