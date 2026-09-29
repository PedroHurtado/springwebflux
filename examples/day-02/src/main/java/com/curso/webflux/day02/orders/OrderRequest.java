package com.curso.webflux.day02.orders;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /api/orders.
 *
 * Aquí solo hay validación ESTRUCTURAL (formato de los datos), que no necesita consultar nada:
 * se resuelve con Bean Validation y, si falla, la respuesta es 400 Bad Request.
 * La validación de NEGOCIO (¿existe el producto?, ¿hay stock?) necesita E/S y se hace de forma
 * reactiva en OrderService; si falla, la respuesta es 422 Unprocessable Content.
 */
public record OrderRequest(
        @NotBlank String customerId,
        @NotEmpty @Size(max = 20) List<@Valid DetailRequest> details) {

    /** Regla que afecta a varias líneas a la vez: un producto no puede repetirse en el pedido. */
    @JsonIgnore   // es una regla de validación, no un dato del JSON
    @AssertTrue(message = "no puede haber productos repetidos en el pedido")
    public boolean isWithoutRepeatedProducts() {
        return details == null
                || details.stream().map(DetailRequest::productId).distinct().count() == details.size();
    }

    public record DetailRequest(@NotBlank String productId, @Positive int quantity) {
    }
}
