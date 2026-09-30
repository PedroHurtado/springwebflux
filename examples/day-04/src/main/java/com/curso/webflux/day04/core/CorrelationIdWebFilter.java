package com.curso.webflux.day04.core;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.curso.webflux.day04.client.CorrelationId;

import reactor.core.publisher.Mono;

/**
 * Lee X-Request-Id de la petición entrante (o genera uno), lo devuelve en la respuesta y lo guarda
 * en el Context de Reactor para que llegue a las llamadas salientes (client/CorrelationIdPropagation).
 *
 * contextWrite se aplica al Mono que devuelve chain.filter(...): el Context viaja "hacia arriba",
 * del suscriptor hacia el origen, así que TODO lo que se ejecute dentro de la cadena
 * (controladores, servicios, WebClient) lo puede leer.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CorrelationIdWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String header = exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER);
        String id = header == null || header.isBlank() ? UUID.randomUUID().toString() : header;
        exchange.getResponse().getHeaders().set(CorrelationId.HEADER, id);
        return chain.filter(exchange)
                .contextWrite(context -> context.put(CorrelationId.CONTEXT_KEY, id));
    }
}
