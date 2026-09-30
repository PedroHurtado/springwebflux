package com.curso.webflux.day03.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Representación de la versión 2.0 de la API (API versioning, novedad de Spring Framework 7).
 *
 * También sirve de ejemplo de anotaciones de Jackson. En Jackson 3 las anotaciones siguen en el
 * paquete com.fasterxml.jackson.annotation (solo el núcleo cambió a tools.jackson).
 */
@JsonPropertyOrder({ "id", "name", "price", "priceWithVat", "available" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductV2(
        String id,
        String name,
        String category,
        BigDecimal price,
        BigDecimal priceWithVat,
        boolean available,
        @JsonProperty("warning") String lowStockWarning) {   // se publica como "warning"; si es null no se escribe (NON_NULL)

    private static final BigDecimal VAT = new BigDecimal("1.21");

    public static ProductV2 from(Product p) {
        return new ProductV2(p.id(), p.name(), p.category(), p.price(),
                p.price().multiply(VAT).setScale(2, RoundingMode.HALF_UP),
                p.stock() > 0,
                p.stock() < 10 ? "Quedan " + p.stock() + " unidades" : null);
    }
}
