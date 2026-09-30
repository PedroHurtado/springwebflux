package com.curso.webflux.day03.client;

import java.util.Optional;

import reactor.util.context.ContextView;

/**
 * Identificador de correlación: el mismo valor viaja por TODA la cadena de llamadas
 * (navegador -> BFF -> pedidos -> catálogo) para poder seguir una petición en los logs.
 *
 * En WebFlux no se puede guardar en un ThreadLocal: una misma petición pasa por varios hilos
 * y un hilo atiende muchas peticiones a la vez. Se guarda en el Context de Reactor, que viaja
 * con la SUSCRIPCIÓN y no con el hilo. Es el mismo mecanismo que usa Spring Security para el
 * SecurityContext (ReactiveSecurityContextHolder) y, por tanto, para reenviar el token.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Request-Id";

    /** Clave en el Context de Reactor (se usa la clase para evitar colisiones con otras claves). */
    public static final Class<CorrelationId> CONTEXT_KEY = CorrelationId.class;

    private CorrelationId() {
    }

    public static Optional<String> from(ContextView context) {
        return context.getOrEmpty(CONTEXT_KEY);
    }
}
