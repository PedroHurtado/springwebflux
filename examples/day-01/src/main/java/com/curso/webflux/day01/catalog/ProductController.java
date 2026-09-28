package com.curso.webflux.day01.catalog;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador anotado: mismo modelo de programación que Spring MVC,
 * pero los métodos devuelven Mono/Flux.
 *
 * Lo localiza el RequestMappingHandlerMapping, lo invoca el RequestMappingHandlerAdapter
 * y el resultado lo escribe el ResponseBodyResultHandler usando los codecs HTTP.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    /**
     * Accept: application/json   -> array JSON (el Flux se agrega antes de escribir).
     * Accept: application/x-ndjson -> un JSON por línea, escrito en streaming.
     */
    @GetMapping
    public Flux<Product> findAll(@RequestParam(required = false) String category) {
        return service.findAll(category);
    }

    @GetMapping("/{id}")
    public Mono<Product> findById(@PathVariable String id) {
        return service.findById(id);
    }

    @GetMapping("/count")
    public Mono<Long> count() {
        return service.findAll(null).count();
    }

    /** @RequestBody Mono<...>: el cuerpo se decodifica de forma no bloqueante. */
    @PostMapping
    public Mono<ResponseEntity<Product>> create(@Valid @RequestBody Mono<ProductRequest> request) {
        return request
                .flatMap(service::create)
                .map(p -> ResponseEntity.created(URI.create("/api/products/" + p.id())).body(p));
    }

    @PutMapping("/{id}")
    public Mono<Product> update(@PathVariable String id, @Valid @RequestBody ProductRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable String id) {
        return service.delete(id);
    }

    /** Server-Sent Events: el navegador lo consume con EventSource. */
    @GetMapping(path = "/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ProductService.PriceChange> prices() {
        return service.priceTicker();
    }
}
