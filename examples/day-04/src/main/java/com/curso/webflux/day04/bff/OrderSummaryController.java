package com.curso.webflux.day04.bff;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * API del BFF. Los errores (404 del pedido, 502 si pedidos no responde) los convierte en ProblemDetail
 * el GlobalExceptionHandler, como en el resto de controladores anotados.
 */
@RestController
@RequestMapping("/api/bff/orders")
public class OrderSummaryController {

    private final OrderSummaryService service;

    public OrderSummaryController(OrderSummaryService service) {
        this.service = service;
    }

    /** Pedido + estado actual de cada producto, en un único JSON. */
    @GetMapping("/{id}")
    public Mono<OrderSummary> summary(@PathVariable String id) {
        return service.summary(id);
    }

    /** Las líneas enriquecidas en streaming: una por línea NDJSON, según responde el catálogo. */
    @GetMapping(path = "/{id}/lines", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<LineView> lines(@PathVariable String id) {
        return service.lines(id);
    }

    /** ❌ Anti-patrón: block() dentro del event loop -> 500 (IllegalStateException). */
    @GetMapping("/{id}/blocking")
    public Mono<OrderSummary> blocking(@PathVariable String id) {
        return service.summaryBlocking(id);
    }
}
