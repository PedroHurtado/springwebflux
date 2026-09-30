package com.curso.webflux.day04;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Día 4 - Tiempo real, vistas, pruebas y cierre.
 *
 * Evoluciona el proyecto del día 3 (catálogo + pedidos + BFF) y añade:
 * - acceso a datos reactivo: Spring Data R2DBC sobre H2 (ProductRepository, OrderRepository con
 *   DatabaseClient, transacción reactiva y bloqueo optimista);
 * - web:     vistas con Thymeleaf (modo normal, data-driven, formularios, Rendering);
 * - ws:      WebSockets (eco, precios en tiempo real con comandos del cliente, chat entre sesiones);
 * - interop: RxJava, CompletableFuture y ReactiveAdapterRegistry.
 * Las pruebas (slices, bindTo..., StepVerifier avanzado, BlockHound) están en src/test.
 */
@SpringBootApplication
public class Day04Application {

    public static void main(String[] args) {
        SpringApplication.run(Day04Application.class, args);
    }
}
