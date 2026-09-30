package com.curso.webflux.day03.client;

import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * Propaga X-Request-Id a TODAS las llamadas salientes.
 *
 * ExchangeFilterFunction es el equivalente de un WebFilter, pero en el cliente: recibe la petición
 * saliente y la siguiente función de la cadena (next).
 *
 * Mono.deferContextual lee el Context de Reactor de la suscripción actual, que escribió
 * core/CorrelationIdWebFilter al recibir la petición entrante. No hay ThreadLocal ni estado compartido.
 *
 * Al ser un WebClientCustomizer, Spring Boot lo aplica al WebClient.Builder autoconfigurado Y a los
 * clientes de @ImportHttpServices (OrdersApi): un único punto para todas las llamadas salientes.
 * ServerBearerExchangeFilterFunction (Spring Security) reenvía el token de acceso con esta misma técnica.
 */
@Component
public class CorrelationIdPropagation implements WebClientCustomizer {

    static final ExchangeFilterFunction FILTER = (request, next) -> Mono.deferContextual(context ->
            next.exchange(CorrelationId.from(context)
                    .map(id -> ClientRequest.from(request).header(CorrelationId.HEADER, id).build())
                    .orElse(request)));

    @Override
    public void customize(WebClient.Builder builder) {
        builder.filter(FILTER);
    }
}
