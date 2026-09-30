package com.curso.webflux.day04.testing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.Validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import com.curso.webflux.day04.orders.DetailCheck.DetailError;
import com.curso.webflux.day04.orders.Order;
import com.curso.webflux.day04.orders.OrderDetail;
import com.curso.webflux.day04.orders.OrderHandler;
import com.curso.webflux.day04.orders.OrderRejectedException;
import com.curso.webflux.day04.orders.OrderRouter;
import com.curso.webflux.day04.orders.OrderService;

import reactor.core.publisher.Mono;

/**
 * Endpoints funcionales SIN Spring: WebTestClient.bindToRouterFunction(...) monta el RouterFunction
 * (rutas + filtros + onError) sobre un WebHandler mínimo. Todo se crea con "new":
 *   OrderRouter -> RouterFunction · OrderHandler con un OrderService de Mockito y un Validator de Bean Validation.
 *
 * Es la prueba más rápida de un endpoint funcional: comprueba rutas, predicados, códigos de estado,
 * validación manual y traducción de errores sin arrancar ningún contexto.
 */
class OrderRoutesUnitTest {

    OrderService service;
    WebTestClient client;

    @BeforeEach
    void setUp() {
        service = mock(OrderService.class);
        var validator = new SpringValidatorAdapter(Validation.buildDefaultValidatorFactory().getValidator());
        var routes = new OrderRouter().orderRoutes(new OrderHandler(service, validator));
        client = WebTestClient.bindToRouterFunction(routes).build();
    }

    @Test
    void createReturns201WithLocation() {
        var detail = new OrderDetail("1", "Teclado", new BigDecimal("10.00"), 2, new BigDecimal("20.00"));
        when(service.create(any())).thenReturn(Mono.just(
                new Order("o-9", "cliente-1", Instant.now(), List.of(detail), new BigDecimal("20.00"))));

        client.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("customerId", "cliente-1", "details", List.of(Map.of("productId", "1", "quantity", 2))))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", ".*/api/orders/o-9")
                .expectBody().jsonPath("$.total").isEqualTo(20.00);
    }

    /** Validación estructural manual (OrderHandler.validate): 400 sin llegar al servicio. */
    @Test
    void invalidBodyIs400AndTheServiceIsNotCalled() {
        client.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("customerId", "", "details", List.of()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.errors.customerId").exists()
                .jsonPath("$.errors.details").exists();

        verifyNoInteractions(service);
    }

    /** onError del RouterFunction: la excepción de negocio del servicio se convierte en ProblemDetail 422. */
    @Test
    void businessErrorIs422ProblemDetail() {
        when(service.create(any())).thenReturn(Mono.error(
                new OrderRejectedException(List.of(new DetailError(0, "99", "el producto no existe")))));

        client.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("customerId", "c", "details", List.of(Map.of("productId", "99", "quantity", 1))))
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.instance").isEqualTo("/api/orders")
                .jsonPath("$.errors[0].productId").isEqualTo("99");
    }

    /** Predicado contentType(APPLICATION_JSON): con otro content-type ninguna ruta encaja -> 404. */
    @Test
    void routePredicatesAreApplied() {
        client.post().uri("/api/orders")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("hola")
                .exchange()
                .expectStatus().isNotFound();
    }
}
