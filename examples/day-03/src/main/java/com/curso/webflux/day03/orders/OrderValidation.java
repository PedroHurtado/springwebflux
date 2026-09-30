package com.curso.webflux.day03.orders;

import java.util.ArrayList;
import java.util.List;

import com.curso.webflux.day03.orders.DetailCheck.DetailError;

import reactor.core.publisher.Mono;

/**
 * Acumulador INMUTABLE del resultado de validar las líneas de un pedido.
 *
 * Se usa con Flux.reduce: cada DetailCheck que llega se "suma" al acumulador, que va guardando
 * por separado las líneas válidas y los errores. Así no hace falta materializar una List<DetailCheck>
 * intermedia, recorrerla dos veces ni hacer casts.
 *
 *   Flux<DetailCheck> ──reduce(empty, add)──► Mono<OrderValidation> ──toDetailsOrReject──► 422 o List<OrderDetail>
 *
 * Es el patrón "Validation" de la programación funcional: acumular errores en lugar de cortar en el primero.
 * Al ser inmutable, la semilla (empty()) se puede compartir sin riesgo entre suscripciones.
 */
public record OrderValidation(List<OrderDetail> details, List<DetailError> errors) {

    private static final OrderValidation EMPTY = new OrderValidation(List.of(), List.of());

    public static OrderValidation empty() {
        return EMPTY;
    }

    /** Devuelve un acumulador NUEVO con la línea añadida (no modifica el actual). */
    public OrderValidation add(DetailCheck check) {
        if (check instanceof DetailCheck.Valid valid) {
            return new OrderValidation(append(details, valid.detail()), errors);
        }
        DetailCheck.Invalid invalid = (DetailCheck.Invalid) check;   // interfaz sellada: no hay más casos
        return new OrderValidation(details, append(errors, invalid.error()));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    /** La decisión final: el pedido sigue (líneas válidas) o se rechaza con TODOS los errores (422). */
    public Mono<List<OrderDetail>> toDetailsOrReject() {
        return isValid() ? Mono.just(details) : Mono.error(new OrderRejectedException(errors));
    }

    /** Copia + elemento: coste O(n) por línea, irrelevante con un máximo de 20 líneas por pedido. */
    private static <T> List<T> append(List<T> list, T item) {
        List<T> copy = new ArrayList<>(list);
        copy.add(item);
        return List.copyOf(copy);
    }
}
