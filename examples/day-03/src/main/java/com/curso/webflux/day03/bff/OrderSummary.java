package com.curso.webflux.day03.bff;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.curso.webflux.day03.client.OrderDto;

/**
 * Respuesta del BFF: un pedido "enriquecido" con el estado actual de cada producto.
 *
 * Es también el ACUMULADOR INMUTABLE del reduce de OrderSummaryService (mismo patrón que
 * OrderValidation del día 2): se parte de start(order), sin líneas, y cada LineView que llega del
 * catálogo produce un resumen nuevo con la línea añadida y los totales actualizados. No hay
 * List<LineView> intermedia ni segundo recorrido para calcular los totales.
 *
 * complete = false si alguna línea quedó en UNKNOWN (el catálogo no respondió): el cliente sabe
 * que la información es parcial.
 */
public record OrderSummary(String orderId, String customerId, Instant createdAt, List<LineView> lines,
                           BigDecimal totalAtPurchase, BigDecimal totalAtCurrentPrices, boolean complete) {

    public static OrderSummary start(OrderDto order) {
        return new OrderSummary(order.id(), order.customerId(), order.createdAt(), List.of(),
                order.total(), BigDecimal.ZERO, true);
    }

    /** Devuelve un resumen NUEVO con la línea añadida (no modifica el actual). */
    public OrderSummary add(LineView line) {
        List<LineView> copy = new ArrayList<>(lines);
        copy.add(line);
        return new OrderSummary(orderId, customerId, createdAt, List.copyOf(copy), totalAtPurchase,
                totalAtCurrentPrices.add(line.currentSubtotal()),
                complete && line.status() != LineView.Status.UNKNOWN);
    }
}
