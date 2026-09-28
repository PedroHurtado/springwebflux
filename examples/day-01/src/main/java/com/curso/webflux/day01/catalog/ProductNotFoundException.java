package com.curso.webflux.day01.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Forma más sencilla de mapear una excepción a un código HTTP.
 * El día 2 veremos @ControllerAdvice y ProblemDetail (RFC 9457).
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String id) {
        super("Producto no encontrado: " + id);
    }
}
