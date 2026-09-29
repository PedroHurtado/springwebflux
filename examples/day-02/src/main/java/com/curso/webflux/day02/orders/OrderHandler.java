package com.curso.webflux.day02.orders;

import org.springframework.stereotype.Component;
import org.springframework.validation.DirectFieldBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * HandlerFunctions del modelo funcional: cada método recibe un ServerRequest y devuelve
 * Mono<ServerResponse>. No hay anotaciones: los datos se extraen explícitamente de la petición.
 *
 * Equivalencias con el modelo anotado:
 *   @PathVariable       -> request.pathVariable("id")
 *   @RequestParam       -> request.queryParam("customerId")  (Optional)
 *   @RequestBody Mono<> -> request.bodyToMono(OrderRequest.class)
 *   @Valid              -> validator.validateObject(...)  (manual)
 *   ResponseEntity      -> ServerResponse.ok()/created(...)
 */
@Component
public class OrderHandler {

    private final OrderService service;
    private final Validator validator;

    public OrderHandler(OrderService service, Validator validator) {
        this.service = service;
        this.validator = validator;   // LocalValidatorFactoryBean de Spring Boot (Bean Validation)
    }

    /** GET /api/orders[?customerId=...] */
    public Mono<ServerResponse> findAll(ServerRequest request) {
        Flux<Order> result = request.queryParam("customerId")
                .map(service::findByCustomer)
                .orElseGet(service::findAll);
        return ServerResponse.ok().body(result, Order.class);
    }

    /** GET /api/orders/{id} */
    public Mono<ServerResponse> findById(ServerRequest request) {
        return service.findById(request.pathVariable("id"))
                .flatMap(order -> ServerResponse.ok().bodyValue(order));
    }

    /**
     * POST /api/orders
     * 400 -> JSON mal formado o validación estructural (Bean Validation, manual).
     * 422 -> validación de negocio en OrderService (producto inexistente, sin stock).
     * 201 -> pedido creado, con cabecera Location absoluta.
     */
    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(OrderRequest.class)
                .switchIfEmpty(Mono.error(() -> new ServerWebInputException("El cuerpo de la petición es obligatorio")))
                .flatMap(this::validate)
                .flatMap(service::create)
                .flatMap(order -> ServerResponse
                        .created(request.uriBuilder().path("/{id}").build(order.id()))   // URI: ver 04-uris.md
                        .bodyValue(order));
    }

    /**
     * Validación manual: sin @Valid, se invoca el Validator y se convierte el resultado en señal.
     * DirectFieldBindingResult accede a los campos directamente, así que resuelve rutas anidadas
     * de records como "details[0].quantity" (validator.validateObject(body) no sabe hacerlo).
     */
    private Mono<OrderRequest> validate(OrderRequest body) {
        Errors errors = new DirectFieldBindingResult(body, "order");
        validator.validate(body, errors);
        return errors.hasErrors() ? Mono.error(new InvalidOrderException(errors)) : Mono.just(body);
    }
}
