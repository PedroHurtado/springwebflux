package com.curso.webflux.day02.catalog;

import java.math.BigDecimal;

import jakarta.validation.constraints.Positive;

/**
 * Objeto de formulario para GET /api/products/search?category=..&maxPrice=..&sort=..
 *
 * Data binding: WebFlux crea el record por su constructor (constructor binding) a partir de
 * los parámetros de la query, convirtiendo cada valor al tipo del componente:
 * "50" -> BigDecimal (conversor estándar), "price-desc" -> ProductSort (conversor propio).
 * Todos son opcionales: si un parámetro no llega, el componente vale null.
 */
public record ProductSearch(String category, @Positive BigDecimal maxPrice, ProductSort sort) {

    public ProductSort sortOrDefault() {
        return sort == null ? ProductSort.NAME : sort;
    }
}
