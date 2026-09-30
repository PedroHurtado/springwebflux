package com.curso.webflux.day03.orders;

import java.math.BigDecimal;

import com.curso.webflux.day03.catalog.Product;

/**
 * Línea (detalle) de un pedido. Su relación con Product es POR IDENTIFICADOR (productId),
 * no por referencia al objeto Product:
 *
 * - Product pertenece a otro agregado (el catálogo) y puede cambiar (precio, stock...).
 * - El pedido guarda una "foto" de lo que importa en el momento de la compra: nombre y precio unitario.
 *   Si mañana sube el precio del producto, el importe del pedido no debe cambiar.
 * - Con una base de datos sería una clave ajena (order_detail.product_id -> product.id).
 */
public record OrderDetail(String productId, String productName, BigDecimal unitPrice, int quantity,
                          BigDecimal subtotal) {

    /** Solo se puede crear a partir de un Product que existe: la validación ya ocurrió antes. */
    public static OrderDetail of(Product product, int quantity) {
        return new OrderDetail(product.id(), product.name(), product.price(), quantity,
                product.price().multiply(BigDecimal.valueOf(quantity)));
    }
}
