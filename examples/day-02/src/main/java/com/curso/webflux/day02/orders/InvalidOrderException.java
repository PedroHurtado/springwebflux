package com.curso.webflux.day02.orders;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.validation.Errors;
import org.springframework.web.ErrorResponseException;

import com.curso.webflux.day02.error.Problems;

/**
 * Validación estructural fallida en un endpoint funcional (400).
 *
 * En un controlador anotado, @Valid produce WebExchangeBindException automáticamente.
 * En un endpoint funcional no hay @Valid: validamos a mano con el Validator de Spring
 * y convertimos el resultado (Errors) en esta excepción.
 */
public class InvalidOrderException extends ErrorResponseException {

    public InvalidOrderException(Errors errors) {
        super(HttpStatus.BAD_REQUEST, Problems.of(HttpStatus.BAD_REQUEST, "validation", "Datos no válidos",
                "La petición contiene " + errors.getErrorCount() + " error(es) de validación"), null);
        Map<String, String> fields = new LinkedHashMap<>();
        errors.getFieldErrors().forEach(error ->
                fields.merge(error.getField(), String.valueOf(error.getDefaultMessage()), (a, b) -> a + "; " + b));
        getBody().setProperty("errors", fields);
    }
}
