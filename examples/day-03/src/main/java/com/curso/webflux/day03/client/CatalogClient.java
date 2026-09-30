package com.curso.webflux.day03.client;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Cliente del microservicio de catálogo escrito "a mano" con WebClient (compárese con OrdersApi,
 * que es declarativo). Aquí se ve cada decisión que hay que tomar en una llamada remota:
 *
 *   ① retrieve() + bodyToMono: la forma habitual; un 4xx/5xx llega como WebClientResponseException.
 *   ② 404 -> Mono vacío: "el producto no existe" es una RESPUESTA válida, no un fallo.
 *   ③ timeout(): límite de negocio para cada intento (más estricto que el read-timeout global).
 *   ④ retryWhen(): reintentos con espera exponencial SOLO para fallos transitorios (5xx, timeout, conexión).
 *
 * Nada bloquea: el método devuelve el Mono y la petición HTTP sale cuando alguien se suscribe.
 * Cada reintento es una re-suscripción, es decir, una petición HTTP nueva.
 */
@Component
public class CatalogClient {

    static final Duration TIMEOUT = Duration.ofMillis(800);

    private static final Retry RETRY = Retry.backoff(2, Duration.ofMillis(100))   // 2 reintentos: ~100 ms y ~200 ms
            .filter(CatalogClient::isTransient);

    private final WebClient webClient;

    public CatalogClient(WebClient catalogWebClient) {
        this.webClient = catalogWebClient;
    }

    public Mono<ProductDto> findProduct(String id) {
        return webClient.get()
                .uri("/api/products/{id}", id)                                      // plantilla: id se codifica
                .retrieve()                                                         // ①
                .bodyToMono(ProductDto.class)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty())   // ②
                .timeout(TIMEOUT)                                                   // ③
                .retryWhen(RETRY);                                                  // ④
    }

    /** 5xx, timeout o error de conexión: al reintentar puede funcionar. Un 400 o un 401, nunca. */
    private static boolean isTransient(Throwable error) {
        return error instanceof WebClientResponseException response && response.getStatusCode().is5xxServerError()
                || error instanceof TimeoutException
                || error instanceof WebClientRequestException;
    }
}
