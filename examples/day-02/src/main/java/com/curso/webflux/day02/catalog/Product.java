package com.curso.webflux.day02.catalog;

import java.math.BigDecimal;

/**
 * Modelo inmutable. Jackson serializa los records sin configuración adicional.
 *
 * Los métodos "with" devuelven una copia modificada: nunca se cambia un objeto compartido,
 * lo que evita problemas de concurrencia cuando varias peticiones leen el mismo producto.
 */
public record Product(String id, String name, String category, BigDecimal price, int stock) {

    public Product withId(String newId) {
        return new Product(newId, name, category, price, stock);
    }

    public Product withPrice(BigDecimal newPrice) {
        return new Product(id, name, category, newPrice, stock);
    }

    public Product withStock(int newStock) {
        return new Product(id, name, category, price, newStock);
    }

    public boolean hasStock(int quantity) {
        return stock >= quantity;
    }
}
