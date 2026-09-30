package com.curso.webflux.day03.orders;

/**
 * Resultado de validar UNA línea del pedido contra el catálogo.
 *
 * En vez de lanzar una excepción en la primera línea incorrecta, cada línea produce un valor:
 * Valid (con el OrderDetail ya construido) o Invalid (con el motivo). Así se pueden reunir
 * TODOS los errores y devolverlos juntos al cliente. Es la idea de los tipos Either/Validation
 * de la programación funcional, expresada con una interfaz sellada de Java 17.
 */
public sealed interface DetailCheck {

    record Valid(OrderDetail detail) implements DetailCheck {
    }

    record Invalid(DetailError error) implements DetailCheck {
    }

    /** Error de una línea: posición en la petición (0..n-1), producto y motivo. */
    record DetailError(int line, String productId, String message) {
    }
}
