package com.curso.webflux.day01.core;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.HandlerAdapter;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.HandlerResultHandler;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.server.WebFilter;

import reactor.core.publisher.Mono;

/**
 * Muestra los "beans especiales" que el DispatcherHandler descubre en el ApplicationContext.
 *
 * GET http://localhost:8080/internals/dispatcher
 */
@RestController
public class DispatcherInfoController {

    private final ApplicationContext context;

    public DispatcherInfoController(ApplicationContext context) {
        this.context = context;
    }

    @GetMapping("/internals/dispatcher")
    public Mono<Map<String, List<String>>> dispatcherBeans() {
        return Mono.fromSupplier(() -> {
            Map<String, List<String>> result = new LinkedHashMap<>();
            result.put("1. HandlerMapping (petición -> handler)", describe(HandlerMapping.class));
            result.put("2. HandlerAdapter (invoca el handler)", describe(HandlerAdapter.class));
            result.put("3. HandlerResultHandler (escribe el resultado)", describe(HandlerResultHandler.class));
            result.put("WebFilter (antes del DispatcherHandler)", describe(WebFilter.class));
            result.put("WebExceptionHandler (errores no gestionados)", describe(WebExceptionHandler.class));
            return result;
        });
    }

    private List<String> describe(Class<?> type) {
        return context.getBeansOfType(type).values().stream()
                .sorted(Comparator.comparingInt(DispatcherInfoController::order))
                .map(bean -> "order=" + order(bean) + " -> " + bean.getClass().getName())
                .toList();
    }

    private static int order(Object bean) {
        return bean instanceof Ordered ordered
                ? ordered.getOrder()
                : OrderUtils.getOrder(bean.getClass(), Ordered.LOWEST_PRECEDENCE);
    }
}
