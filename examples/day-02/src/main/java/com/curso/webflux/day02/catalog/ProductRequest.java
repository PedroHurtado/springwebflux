package com.curso.webflux.day02.catalog;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada con Bean Validation. Los mensajes pueden personalizarse con "message"
 * o con claves en ValidationMessages.properties.
 */
public record ProductRequest(
        @NotBlank @Size(max = 60) String name,
        @NotBlank String category,
        @NotNull @Positive BigDecimal price,
        @PositiveOrZero int stock) {

    public Product toProduct(String id) {
        return new Product(id, name, category, price, stock);
    }
}
