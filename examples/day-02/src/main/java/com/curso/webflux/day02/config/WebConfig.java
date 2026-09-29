package com.curso.webflux.day02.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.reactive.config.ApiVersionConfigurer;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Configuración de WebFlux con Spring Boot: se implementa WebFluxConfigurer y se sobrescriben
 * solo los métodos necesarios.
 *
 * ⚠️ NO se anota con @EnableWebFlux: en Spring Boot eso desactivaría la autoconfiguración
 * de WebFlux (codecs, recursos estáticos, conversores de Boot...). @EnableWebFlux es para
 * aplicaciones Spring sin Boot.
 */
@Configuration
public class WebConfig implements WebFluxConfigurer {

    /** Conversión de tipos para @RequestParam, @PathVariable, @ModelAttribute... */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToProductSortConverter());
    }

    /**
     * CORS global. Se aplica a los HandlerMapping de WebFlux: controladores anotados Y endpoints
     * funcionales. Se combina con @CrossOrigin (ProductController añade http://localhost:5173).
     *
     * http://127.0.0.1:8080 es otro ORIGEN distinto de http://localhost:8080 (cambia el host):
     * abre http://127.0.0.1:8080/cors.html para probarlo en el navegador.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://127.0.0.1:8080")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("Content-Type", "API-Version")
                .exposedHeaders("Location", "X-Response-Time")   // cabeceras que el JS puede leer
                .maxAge(1800);                                    // segundos que el navegador cachea el preflight
    }

    /**
     * API versioning (Spring Framework 7). La versión se lee de la cabecera API-Version.
     * Sin cabecera se usa la 1.0. Una versión no soportada (p. ej. 3.0) -> 400.
     * Equivalente con propiedades de Boot: spring.webflux.apiversion.use.header=API-Version, etc.
     */
    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.useRequestHeader("API-Version")
                .addSupportedVersions("1.0", "2.0")
                .setDefaultVersion("1.0");
    }
}
