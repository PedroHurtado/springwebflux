package com.curso.webflux.day02.orders;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import com.curso.webflux.day02.error.Problems;
import com.curso.webflux.day02.orders.DetailCheck.DetailError;

/**
 * El pedido está bien formado pero incumple reglas de negocio (producto inexistente, sin stock...).
 *
 * 422 Unprocessable Content: "entiendo la petición, pero no puedo procesarla".
 * Al extender ErrorResponseException la excepción LLEVA su ProblemDetail: no hace falta ningún
 * @ExceptionHandler; basta con ServerResponse.from(ex) en el endpoint funcional (ver OrderRouter).
 */
public class OrderRejectedException extends ErrorResponseException {

    public OrderRejectedException(List<DetailError> errors) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, Problems.of(HttpStatus.UNPROCESSABLE_CONTENT, "order-rejected",
                "Pedido rechazado", "El pedido tiene " + errors.size() + " línea(s) no válida(s)"), null);
        getBody().setProperty("errors", errors);
    }
}
