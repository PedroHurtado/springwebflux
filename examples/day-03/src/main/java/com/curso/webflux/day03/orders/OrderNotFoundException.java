package com.curso.webflux.day03.orders;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import com.curso.webflux.day03.error.Problems;

/** 404 con ProblemDetail incorporado (ErrorResponseException). */
public class OrderNotFoundException extends ErrorResponseException {

    public OrderNotFoundException(String orderId) {
        super(HttpStatus.NOT_FOUND, Problems.of(HttpStatus.NOT_FOUND, "order-not-found",
                "Pedido no encontrado", "Pedido no encontrado: " + orderId), null);
        getBody().setProperty("orderId", orderId);
    }
}
