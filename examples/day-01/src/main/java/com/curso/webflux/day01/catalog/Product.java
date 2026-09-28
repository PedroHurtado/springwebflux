package com.curso.webflux.day01.catalog;

import java.math.BigDecimal;

/**
 * Modelo inmutable. Jackson serializa los records sin configuración adicional.
 */
public record Product(String id, String name, String category, BigDecimal price, int stock) {

    public Product withId(String newId) {
        return new Product(newId, name, category, price, stock);
    }

    public Product withPrice(BigDecimal newPrice) {
        return new Product(id, name, category, newPrice, stock);
    }
}
