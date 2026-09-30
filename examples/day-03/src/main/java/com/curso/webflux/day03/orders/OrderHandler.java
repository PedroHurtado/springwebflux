package com.curso.webflux.day03.orders;

import java.time.Duration;

import org.springframework.http.CacheControl;
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

    /**
     * GET /api/orders/{id}
     *
     * Día 3 — caché HTTP de un recurso INMUTABLE: un pedido no cambia nunca una vez creado.
     *   - Cache-Control: max-age=3600, private -> el navegador lo reutiliza 1 h sin preguntar
     *     (private: contiene datos de un cliente, un proxy compartido NO debe guardarlo).
     *   - ETag y Last-Modified -> pasada la hora, se revalida con If-None-Match / If-Modified-Since.
     * ServerResponse comprueba las cabeceras condicionales y responde 304 él solo, igual que ResponseEntity.
     */
    public Mono<ServerResponse> findById(ServerRequest request) {
        return service.findById(request.pathVariable("id"))
                .flatMap(order -> ServerResponse.ok()
                        .eTag(order.id())                            // inmutable: el id identifica la versión
                        .lastModified(order.createdAt())
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                        .bodyValue(order));
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
