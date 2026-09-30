package com.curso.webflux.day04.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import reactor.core.publisher.Mono;

/**
 * HTTP Service Client: el cliente del microservicio de pedidos se DECLARA como una interfaz y
 * Spring genera la implementación (un proxy que usa WebClient por debajo).
 *
 * Como los métodos devuelven Mono/Flux, la llamada es NO bloqueante: el proxy devuelve el Mono en el
 * acto, sin esperar la respuesta. Si devolviesen OrderDto (sin Mono) la llamada sería bloqueante y,
 * dentro de un event loop, fallaría (ver OrderSummaryService.summaryBlocking).
 *
 * Se registra en ClientConfig con @ImportHttpServices (grupo "orders"); la URL base sale de
 * spring.http.serviceclient.orders.base-url (application.properties).
 */
@HttpExchange("/api/orders")
public interface OrdersApi {

    /** GET /api/orders/{id}. Un 404 llega como WebClientResponseException.NotFound. */
    @GetExchange("/{id}")
    Mono<OrderDto> findById(@PathVariable String id);
}
