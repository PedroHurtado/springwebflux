package com.curso.webflux.day01;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Día 1 - Catálogo reactivo.
 *
 * Arranca una aplicación Spring WebFlux sobre Reactor Netty (servidor por defecto
 * de spring-boot-starter-webflux). Fíjate en el log de arranque:
 * "Netty started on port 8080".
 */
@SpringBootApplication
public class Day01Application {

    public static void main(String[] args) {
        SpringApplication.run(Day01Application.class, args);
    }
}
