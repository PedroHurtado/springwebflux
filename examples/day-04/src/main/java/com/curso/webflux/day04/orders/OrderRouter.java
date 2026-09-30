package com.curso.webflux.day04.orders;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Mono;

/**
 * Endpoints funcionales: las rutas se declaran como DATOS (un bean RouterFunction), no con anotaciones.
 *
 * Los registra el RouterFunctionMapping (el segundo HandlerMapping que vimos en /internals/dispatcher
 * el día 1) y los invoca el HandlerFunctionAdapter.
 */
@Configuration
public class OrderRouter {

    private static final Logger log = LoggerFactory.getLogger(OrderRouter.class);

    @Bean
    public RouterFunction<ServerResponse> orderRoutes(OrderHandler handler) {
        return route()
                // Rutas anidadas: todas comparten el prefijo /api/orders
                .path("/api/orders", orders -> orders
                        .GET("/{id}", accept(MediaType.APPLICATION_JSON), handler::findById)
                        .GET(handler::findAll)                                        // GET /api/orders
                        .POST(contentType(MediaType.APPLICATION_JSON), handler::create))
                // Filtro: se aplica solo a las rutas de este builder (no a toda la aplicación como un WebFilter)
                .filter((request, next) -> {
                    log.debug("[orders] {} {}", request.method(), request.path());
                    return next.handle(request);
                })
                // Gestión de errores: @ControllerAdvice NO se aplica a endpoints funcionales.
                // Toda excepción que sea ErrorResponse (las nuestras y las de Spring: 400 de JSON mal formado,
                // 415...) se escribe como ProblemDetail (application/problem+json).
                .onError(ErrorResponse.class::isInstance,
                        (error, request) -> problem((ErrorResponse) error, request))
                .build();
    }

    /**
     * ErrorResponse -> ServerResponse con cuerpo ProblemDetail.
     * Hacemos a mano lo que ResponseEntityExceptionHandler hace en los controladores anotados:
     * content-type application/problem+json y "instance" con la ruta de la petición.
     * (ServerResponse.from(errorResponse) también sirve, pero responde con application/json.)
     */
    private static Mono<ServerResponse> problem(ErrorResponse error, ServerRequest request) {
        ProblemDetail body = error.getBody();
        if (body.getInstance() == null) {
            body.setInstance(URI.create(request.path()));
        }
        return ServerResponse.status(error.getStatusCode())
                .headers(headers -> headers.addAll(error.getHeaders()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(body);
    }
}
