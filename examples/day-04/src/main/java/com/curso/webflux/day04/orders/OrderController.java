package com.curso.webflux.day04.orders;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Los MISMOS pedidos que OrderRouter/OrderHandler, pero con un controlador anotado.
 *
 * Sirve para comparar tres cosas (ver docs/day-02/02-pedidos-validacion-reactiva.md):
 *   1. Este controlador frente al de Spring MVC: el cuerpo llega como Mono<OrderRequest> (no como
 *      OrderRequest) y se devuelve Mono<ResponseEntity<Order>> (no ResponseEntity<Order>).
 *   2. Este controlador frente al endpoint funcional (OrderHandler): mismo servicio, otro modelo de programación.
 *   3. La validación de NEGOCIO no está aquí: está en OrderService.create, compartida por los dos modelos.
 *
 * Validación en dos niveles:
 *   - @Valid @RequestBody Mono<...> -> estructural (Bean Validation) -> onError(WebExchangeBindException) -> 400
 *   - service.create(...)  -> negocio (existe el producto, hay stock) -> OrderRejectedException -> 422
 * Ambas excepciones las convierte en ProblemDetail el GlobalExceptionHandler (@RestControllerAdvice),
 * que sí se aplica aquí porque es un controlador anotado.
 */
@RestController
@RequestMapping("/api/annotated/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<Order> findAll(@RequestParam(required = false) String customerId) {
        return customerId == null ? service.findAll() : service.findByCustomer(customerId);
    }

    @GetMapping("/{id}")
    public Mono<Order> findById(@PathVariable String id) {
        return service.findById(id);                                  // 404 -> OrderNotFoundException
    }

    /**
     * Compárala con la versión Spring MVC:
     *
     *   public ResponseEntity<Order> create(@Valid @RequestBody OrderRequest request, UriComponentsBuilder uriBuilder) {
     *       Order order = service.create(request);                   // el hilo ESPERA aquí
     *       return ResponseEntity.created(...).body(order);
     *   }
     *
     * @RequestBody Mono<OrderRequest>: el método se invoca en cuanto llegan las cabeceras, SIN esperar
     * al cuerpo. El cuerpo es un Mono que se decodifica (y se valida con @Valid) cuando alguien se
     * suscribe, es decir, cuando WebFlux se suscribe al Mono que devolvemos.
     *
     * Consecuencia importante: un error de @Valid ya NO se lanza antes de entrar en el método.
     * Llega como señal onError(WebExchangeBindException) DENTRO de este Mono; como no la tratamos
     * (no hay onErrorResume), sigue hasta GlobalExceptionHandler -> 400. Podríamos capturarla aquí:
     *     request.onErrorResume(WebExchangeBindException.class, ex -> ...)
     *
     * Flujo completo, un único pipeline:
     *   cuerpo -> @Valid (400) -> service.create: validación de negocio (422) -> 201 Created
     */
    @PostMapping
    public Mono<ResponseEntity<Order>> create(@Valid @RequestBody Mono<OrderRequest> request,
                                              UriComponentsBuilder uriBuilder) {
        return request                                     // Mono<OrderRequest>: aún no se ha leído el cuerpo
                .flatMap(service::create)                  // flatMap: service.create devuelve otro Mono
                .map(order -> {                            // solo si el pedido es válido (400 y 422 lo saltan)
                    URI location = uriBuilder.path("/api/annotated/orders/{id}").buildAndExpand(order.id()).toUri();
                    return ResponseEntity.created(location).body(order);
                });
    }
}
