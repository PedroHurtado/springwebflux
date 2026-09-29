package com.curso.webflux.day02.orders;

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
 *   1. Este controlador frente al de Spring MVC: la firma es casi idéntica; solo cambia el tipo de retorno
 *      (Mono<ResponseEntity<Order>> en vez de ResponseEntity<Order>).
 *   2. Este controlador frente al endpoint funcional (OrderHandler): mismo servicio, otro modelo de programación.
 *   3. La validación de NEGOCIO no está aquí: está en OrderService.create, compartida por los dos modelos.
 *
 * Validación en dos niveles:
 *   - @Valid @RequestBody  -> estructural (Bean Validation) -> WebExchangeBindException -> 400
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
     * Aquí service.create devuelve un Mono: el método termina enseguida y la respuesta se construye
     * con map(...) cuando el pedido está validado y guardado. Si la validación de negocio falla,
     * el Mono emite OrderRejectedException, el map no se ejecuta y la respuesta es 422.
     */
    @PostMapping
    public Mono<ResponseEntity<Order>> create(@Valid @RequestBody OrderRequest request,
                                              UriComponentsBuilder uriBuilder) {
        return service.create(request)
                .map(order -> {
                    URI location = uriBuilder.path("/api/annotated/orders/{id}").buildAndExpand(order.id()).toUri();
                    return ResponseEntity.created(location).body(order);
                });
    }
}
