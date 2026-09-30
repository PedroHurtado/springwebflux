package com.curso.webflux.day04.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Pedido: raíz del agregado. Contiene sus líneas (OrderDetail) y el total calculado.
 *
 * Jackson 3 serializa Instant como texto ISO-8601 ("2026-09-29T08:15:30.123Z") sin configuración
 * adicional (en Jackson 2 había que desactivar WRITE_DATES_AS_TIMESTAMPS).
 */
public record Order(String id, String customerId, Instant createdAt, List<OrderDetail> details, BigDecimal total) {

    /**
     * Crea un pedido nuevo (sin id) calculando el total a partir de las líneas.
     * Día 4: la fecha se trunca a milisegundos para que coincida con lo que se guarda en la BD
     * (la respuesta del POST y la de un GET posterior deben ser idénticas).
     */
    public static Order create(String customerId, List<OrderDetail> details) {
        BigDecimal total = details.stream()
                .map(OrderDetail::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Order(null, customerId, Instant.now().truncatedTo(ChronoUnit.MILLIS), List.copyOf(details), total);
    }

    public Order withId(String newId) {
        return new Order(newId, customerId, createdAt, details, total);
    }
}
