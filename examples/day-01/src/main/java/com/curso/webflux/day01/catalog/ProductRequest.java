package com.curso.webflux.day01.catalog;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * DTO de entrada. La validación se profundiza el día 2; hoy basta con ver
 * que @Valid funciona igual que en Spring MVC.
 */
public record ProductRequest(
        @NotBlank String name,
        @NotBlank String category,
        @NotNull @Positive BigDecimal price,
        @PositiveOrZero int stock) {

    public Product toProduct(String id) {
        return new Product(id, name, category, price, stock);
    }
}
