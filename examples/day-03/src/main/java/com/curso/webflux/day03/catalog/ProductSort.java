package com.curso.webflux.day03.catalog;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Criterio de ordenación del buscador.
 *
 * En la URL se escribe en formato "amigable" (?sort=price-desc). La conversión
 * String -> ProductSort la hace StringToProductSortConverter, registrado en WebConfig.addFormatters.
 */
public enum ProductSort {

    NAME("name", Comparator.comparing(Product::name)),
    PRICE_ASC("price-asc", Comparator.comparing(Product::price)),
    PRICE_DESC("price-desc", Comparator.comparing(Product::price).reversed());

    private final String code;
    private final Comparator<Product> comparator;

    ProductSort(String code, Comparator<Product> comparator) {
        this.code = code;
        this.comparator = comparator;
    }

    public Comparator<Product> comparator() {
        return comparator;
    }

    public static ProductSort fromCode(String code) {
        return Arrays.stream(values())
                .filter(sort -> sort.code.equalsIgnoreCase(code) || sort.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Orden no válido: '" + code + "'. Valores admitidos: name, price-asc, price-desc"));
    }
}
