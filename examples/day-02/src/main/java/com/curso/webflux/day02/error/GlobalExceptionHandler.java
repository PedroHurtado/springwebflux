package com.curso.webflux.day02.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.ServerWebExchange;

import com.curso.webflux.day02.catalog.ProductImageStore;
import com.curso.webflux.day02.catalog.ProductNotFoundException;

import reactor.core.publisher.Mono;

/**
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody: sus @ExceptionHandler se aplican
 * a TODOS los controladores anotados. OJO: no se aplica a los endpoints funcionales (orders),
 * que gestionan sus errores con RouterFunctions.Builder.onError (ver OrderRouter).
 *
 * Extender ResponseEntityExceptionHandler hace que las excepciones propias de Spring WebFlux
 * (400, 404, 405, 406, 415, validación...) se respondan como ProblemDetail (application/problem+json).
 * Es lo mismo que activa la propiedad spring.webflux.problemdetails.enabled=true, pero aquí
 * además podemos personalizar cada respuesta.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Excepción de negocio -> ProblemDetail. Devolver ProblemDetail fija el estado y el content-type. */
    @ExceptionHandler
    public ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
        ProblemDetail problem = Problems.of(HttpStatus.NOT_FOUND, "product-not-found",
                "Producto no encontrado", ex.getMessage());
        problem.setProperty("productId", ex.getProductId());   // propiedad extra: se serializa al mismo nivel
        return problem;
    }

    /** Imagen demasiado grande (DataBufferUtils.join con límite). */
    @ExceptionHandler
    public ProblemDetail handleTooLarge(DataBufferLimitException ex) {
        return Problems.of(HttpStatus.CONTENT_TOO_LARGE, "payload-too-large", "Fichero demasiado grande",
                "El tamaño máximo es " + ProductImageStore.MAX_IMAGE_BYTES / 1024 + " KB");
    }

    /** @Valid sobre @RequestBody o @ModelAttribute: añadimos el detalle campo -> mensaje. */
    @Override
    protected Mono<ResponseEntity<Object>> handleWebExchangeBindException(
            WebExchangeBindException ex, HttpHeaders headers, HttpStatusCode status, ServerWebExchange exchange) {

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getFieldErrors()) {
            // isBindingFailure: el valor no se pudo CONVERTIR al tipo (p. ej. ?maxPrice=abc, ?sort=raro).
            // El mensaje por defecto es muy técnico; lo sustituimos por uno legible.
            String message = error.isBindingFailure()
                    ? "valor no válido: '" + error.getRejectedValue() + "'"
                    : String.valueOf(error.getDefaultMessage());
            errors.merge(error.getField(), message, (a, b) -> a + "; " + b);
        }
        ProblemDetail problem = ex.getBody();
        problem.setType(Problems.type("validation"));
        problem.setTitle("Datos no válidos");
        problem.setDetail("La petición contiene " + errors.size() + " campo(s) no válido(s)");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, exchange);
    }

    /** Restricciones sobre parámetros sueltos (@Min, @Max... en @RequestParam, @PathVariable). */
    @Override
    protected Mono<ResponseEntity<Object>> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, ServerWebExchange exchange) {

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(error ->
                errors.merge(result.getMethodParameter().getParameterName(),
                        String.valueOf(error.getDefaultMessage()), (a, b) -> a + "; " + b)));
        ProblemDetail problem = ex.getBody();
        problem.setType(Problems.type("validation"));
        problem.setTitle("Parámetros no válidos");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, exchange);
    }
}
