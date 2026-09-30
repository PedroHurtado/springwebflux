package com.curso.webflux.day03;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Día 3 - Cliente reactivo, seguridad y rendimiento HTTP.
 *
 * Evoluciona el proyecto del día 2 (catálogo + pedidos) y añade:
 * - client: WebClient (CatalogClient), HTTP Service Client (OrdersApi) y propagación de contexto.
 * - bff:    un Backend For Frontend que encadena llamadas a pedidos y catálogo SIN bloquear.
 * - core:   CorrelationIdWebFilter (X-Request-Id en el Context de Reactor).
 * - caché HTTP (ETag, Last-Modified, Cache-Control, Range) y HTTP/2 (h2c y, con el perfil tls, h2).
 * La seguridad (Spring Security, tokens entre microservicios) se trata solo en la documentación.
 */
@SpringBootApplication
public class Day03Application {

    public static void main(String[] args) {
        SpringApplication.run(Day03Application.class, args);
    }
}
