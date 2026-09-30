package com.curso.webflux.day04.interop;

import java.util.concurrent.CompletableFuture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.curso.webflux.day04.catalog.Product;
import com.curso.webflux.day04.catalog.ProductService;
import com.curso.webflux.day04.catalog.ProductV2;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Día 4 — Bibliotecas reactivas (II): WebFlux NO obliga a usar Reactor en los controladores.
 *
 * Los tipos de retorno (y los @RequestBody) se adaptan con el ReactiveAdapterRegistry: un registro de
 * "adaptadores" entre cada tipo asíncrono conocido y un Publisher de Reactive Streams. WebFlux lo consulta
 * para saber si un valor es reactivo, si es de uno o de varios elementos y cómo suscribirse a él.
 *
 *   Reactor (Mono, Flux) · RxJava 3 (Single, Maybe, Flowable, Observable, Completable) ·
 *   JDK (CompletableFuture, Flow.Publisher) · Kotlin (corrutinas, Flow) · SmallRye Mutiny (Uni, Multi)
 *
 * La conversión Reactor <-> RxJava es directa porque ambos implementan Reactive Streams (Publisher):
 * Flowable.fromPublisher(flux), Flux.from(flowable). Nada se bloquea ni se copia a una lista.
 */
@RestController
@RequestMapping("/api/interop")
public class InteropController {

    private final ProductService service;

    public InteropController(ProductService service) {
        this.service = service;
    }

    /** Flowable (RxJava, varios elementos, con backpressure): se serializa igual que un Flux. */
    @GetMapping("/rx/low-stock")
    public Flowable<Product> lowStock(@RequestParam(defaultValue = "10") int max) {
        return Flowable.fromPublisher(service.findAll(null))
                .filter(product -> product.stock() < max);          // operador de RxJava
    }

    /**
     * Single (RxJava, exactamente un elemento): equivale a un Mono que no puede estar vacío.
     * El error (ProductNotFoundException -> 404) pasa por el mismo @RestControllerAdvice que con Mono.
     */
    @GetMapping("/rx/products/{id}")
    public Single<ProductV2> product(@PathVariable String id) {
        return Single.fromPublisher(service.findById(id))
                .map(ProductV2::from);
    }

    /**
     * CompletableFuture (JDK): también es un valor asíncrono que WebFlux sabe esperar.
     * ⚠️ A diferencia de Mono, un CompletableFuture es IMPACIENTE: toFuture() se suscribe EN EL ACTO (la
     * consulta empieza aunque nadie espere el resultado) y no se puede cancelar ni reintentar suscribiéndose
     * de nuevo. Úsalo en los bordes (integrar una API que lo exige), no dentro de un pipeline.
     */
    @GetMapping("/future/count")
    public CompletableFuture<Long> count() {
        return service.findAll(null).count().toFuture();
    }
}
