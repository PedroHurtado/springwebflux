package com.curso.webflux.day02.catalog;

/**
 * Excepción de negocio "pura": no sabe nada de HTTP.
 *
 * El día 1 llevaba @ResponseStatus(404). Hoy la traduce a ProblemDetail (RFC 9457)
 * un @ExceptionHandler de error/GlobalExceptionHandler. Compárala con las excepciones de
 * orders, que extienden ErrorResponseException y ya llevan su ProblemDetail dentro.
 */
public class ProductNotFoundException extends RuntimeException {

    private final String productId;

    public ProductNotFoundException(String productId) {
        super("Producto no encontrado: " + productId);
        this.productId = productId;
    }

    public String getProductId() {
        return productId;
    }
}
