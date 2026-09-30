package com.curso.webflux.day04.bff;

import java.math.BigDecimal;

import com.curso.webflux.day04.client.OrderDto.LineDto;
import com.curso.webflux.day04.client.ProductDto;

/**
 * Una línea del pedido tal y como la ve el cliente del BFF: lo que se compró (datos del microservicio
 * de pedidos) + cómo está ahora el producto (datos del microservicio de catálogo).
 *
 * currentPrice es null cuando no se sabe (producto retirado o catálogo caído): el BFF DEGRADA la
 * respuesta en lugar de fallar entera.
 */
public record LineView(String productId, String productName, int quantity, BigDecimal priceAtPurchase,
                       BigDecimal currentPrice, Status status) {

    public enum Status {
        /** El producto existe y hay stock para volver a pedir la misma cantidad. */
        AVAILABLE,
        /** El producto existe, pero sin stock suficiente. */
        OUT_OF_STOCK,
        /** El catálogo respondió 404: el producto se ha retirado. */
        DISCONTINUED,
        /** El catálogo no respondió a tiempo o falló tras los reintentos: no se sabe. */
        UNKNOWN
    }

    public static LineView of(LineDto line, ProductDto product) {
        Status status = product.stock() >= line.quantity() ? Status.AVAILABLE : Status.OUT_OF_STOCK;
        return new LineView(line.productId(), product.name(), line.quantity(), line.unitPrice(), product.price(), status);
    }

    public static LineView discontinued(LineDto line) {
        return withoutProduct(line, Status.DISCONTINUED);
    }

    public static LineView unknown(LineDto line) {
        return withoutProduct(line, Status.UNKNOWN);
    }

    private static LineView withoutProduct(LineDto line, Status status) {
        return new LineView(line.productId(), line.productName(), line.quantity(), line.unitPrice(), null, status);
    }

    /** Importe a precio actual; cero si no se conoce el precio. */
    public BigDecimal currentSubtotal() {
        return currentPrice == null ? BigDecimal.ZERO : currentPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
