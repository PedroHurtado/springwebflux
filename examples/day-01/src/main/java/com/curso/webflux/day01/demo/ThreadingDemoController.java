package com.curso.webflux.day01.demo;

import java.time.Duration;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Demostración del modelo de hilos (event loop) de WebFlux.
 *
 * Probar en paralelo con una herramienta de carga, por ejemplo:
 *   hey -n 200 -c 50 "http://localhost:8080/api/demo/non-blocking?ms=1000"
 *   hey -n 200 -c 50 "http://localhost:8080/api/demo/blocking?ms=1000"
 * o simplemente abrir varias pestañas del navegador a la vez.
 */
@RestController
@RequestMapping("/api/demo")
public class ThreadingDemoController {

    /** Espera sin bloquear: el hilo reactor-http-nio-* queda libre durante la espera. */
    @GetMapping("/non-blocking")
    public Mono<Map<String, String>> nonBlocking(@RequestParam(defaultValue = "1000") long ms) {
        String requestThread = Thread.currentThread().getName();
        return Mono.delay(Duration.ofMillis(ms))
                .map(tick -> Map.of(
                        "tipo", "no bloqueante (Mono.delay)",
                        "hiloPeticion", requestThread,
                        "hiloRespuesta", Thread.currentThread().getName()));
    }

    /**
     * ANTIPATRÓN: Thread.sleep en un hilo del event loop.
     * Con pocos hilos (1 por núcleo) unas pocas peticiones concurrentes bloquean el servidor.
     */
    @GetMapping("/blocking")
    public Mono<Map<String, String>> blocking(@RequestParam(defaultValue = "1000") long ms) throws InterruptedException {
        Thread.sleep(ms);
        return Mono.just(Map.of(
                "tipo", "BLOQUEANTE en event loop (no hacer)",
                "hilo", Thread.currentThread().getName()));
    }

    /**
     * Si no hay alternativa reactiva (JDBC, librería legada...), aislar la llamada
     * bloqueante en Schedulers.boundedElastic() con subscribeOn.
     */
    @GetMapping("/offloaded")
    public Mono<Map<String, String>> offloaded(@RequestParam(defaultValue = "1000") long ms) {
        String requestThread = Thread.currentThread().getName();
        return Mono.fromCallable(() -> {
                    Thread.sleep(ms); // simula una llamada bloqueante legada
                    return Map.of(
                            "tipo", "bloqueante aislado en boundedElastic",
                            "hiloPeticion", requestThread,
                            "hiloTrabajo", Thread.currentThread().getName());
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
