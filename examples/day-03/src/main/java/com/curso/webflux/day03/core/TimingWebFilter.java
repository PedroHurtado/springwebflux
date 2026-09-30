package com.curso.webflux.day03.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * WebFilter: pieza de la WebHandler API (núcleo reactivo).
 *
 * Se ejecuta ANTES del DispatcherHandler, tanto para controladores anotados como para
 * endpoints funcionales. Trabaja sobre ServerWebExchange (petición + respuesta + atributos).
 *
 * Ojo: la cabecera se añade con beforeCommit porque, cuando chain.filter(...) termina,
 * la respuesta puede estar ya enviada y sus cabeceras serían de solo lectura.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TimingWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(TimingWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long start = System.nanoTime();
        exchange.getResponse().beforeCommit(() -> {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            exchange.getResponse().getHeaders().add("X-Response-Time", elapsedMs + "ms");
            return Mono.empty();
        });
        return chain.filter(exchange)
                .doFinally(signal -> log.debug("[{}] [{}] {} {} -> {} ({} ms, hilo {})",
                        exchange.getLogPrefix().trim(),
                        exchange.getResponse().getHeaders().getFirst("X-Request-Id"),   // día 3: CorrelationIdWebFilter
                        exchange.getRequest().getMethod(),
                        exchange.getRequest().getPath(),
                        exchange.getResponse().getStatusCode(),
                        (System.nanoTime() - start) / 1_000_000,
                        Thread.currentThread().getName()));
    }
}
