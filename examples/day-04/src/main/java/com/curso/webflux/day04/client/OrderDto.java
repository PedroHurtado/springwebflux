package com.curso.webflux.day04.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Contrato del microservicio de pedidos visto desde el BFF (tolerant reader, ver ProductDto). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderDto(String id, String customerId, Instant createdAt, List<LineDto> details, BigDecimal total) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LineDto(String productId, String productName, BigDecimal unitPrice, int quantity) {
    }
}
