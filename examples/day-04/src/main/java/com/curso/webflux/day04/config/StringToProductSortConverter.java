package com.curso.webflux.day04.config;

import org.springframework.core.convert.converter.Converter;

import com.curso.webflux.day04.catalog.ProductSort;

/**
 * Conversión de tipos: "price-desc" -> ProductSort.PRICE_DESC.
 *
 * Sin este conversor Spring solo aceptaría el nombre exacto del enum (?sort=PRICE_DESC).
 * Si la conversión falla (IllegalArgumentException) el error se informa como error de binding (400).
 */
public class StringToProductSortConverter implements Converter<String, ProductSort> {

    @Override
    public ProductSort convert(String source) {
        return ProductSort.fromCode(source.trim());
    }
}
