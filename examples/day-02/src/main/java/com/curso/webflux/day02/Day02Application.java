package com.curso.webflux.day02;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Día 2 - Modelos de programación del servidor.
 *
 * Evoluciona el catálogo del día 1 y añade un segundo dominio (pedidos):
 * - catalog: controladores anotados (II): data binding, validación, ProblemDetail, multipart, versionado.
 * - orders:  puntos finales funcionales (RouterFunction) y validación de negocio reactiva.
 * - config:  WebFluxConfigurer (conversión, CORS, API versioning).
 */
@SpringBootApplication
public class Day02Application {

    public static void main(String[] args) {
        SpringApplication.run(Day02Application.class, args);
    }
}
