package com.curso.webflux.day03.error;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Utilidades para construir ProblemDetail (RFC 9457) de forma homogénea.
 *
 * "type" identifica la CLASE de problema (documentable, estable); "detail" explica ESTE caso concreto.
 * Se usa el dominio reservado .example (RFC 2606): en un proyecto real apuntaría a la documentación de la API.
 */
public final class Problems {

    private static final String BASE = "https://curso-webflux.example/problems/";

    private Problems() {
    }

    public static URI type(String slug) {
        return URI.create(BASE + slug);
    }

    public static ProblemDetail of(HttpStatus status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type(slug));
        problem.setTitle(title);
        return problem;
    }
}
