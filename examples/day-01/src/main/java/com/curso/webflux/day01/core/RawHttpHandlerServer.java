package com.curso.webflux.day01.core;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * Núcleo reactivo SIN Spring Boot y SIN DispatcherHandler.
 *
 * Nivel 1: HttpHandler -> contrato mínimo (request, response) -> Mono<Void>.
 * Nivel 2: WebHandler + WebFilter montados con WebHttpHandlerBuilder
 *          (es lo que Spring Boot hace por nosotros, con el DispatcherHandler como WebHandler).
 *
 * Ejecutar: mvn compile exec:java -Dexec.mainClass=com.curso.webflux.day01.core.RawHttpHandlerServer
 * (o botón "Run" en el IDE) y probar:
 *   curl -i http://localhost:8081/          (HttpHandler "a pelo")
 *   curl -i http://localhost:8082/hola      (WebHandler + WebFilter)
 */
public class RawHttpHandlerServer {

    public static void main(String[] args) {
        // ---------- Nivel 1: HttpHandler ----------
        HttpHandler rawHandler = (request, response) -> {
            response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
            String body = "Hola desde un HttpHandler: " + request.getMethod() + " " + request.getURI()
                    + " (hilo " + Thread.currentThread().getName() + ")\n";
            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        };

        // ---------- Nivel 2: WebHandler + WebFilter ----------
        WebHandler webHandler = exchange -> {
            String name = exchange.getRequest().getPath().value().replace("/", "");
            String body = "Hola " + (name.isBlank() ? "mundo" : name) + " desde un WebHandler\n";
            exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return exchange.getResponse().writeWith(Mono.just(buffer));
        };
        WebFilter poweredBy = (exchange, chain) -> {
            exchange.getResponse().getHeaders().add("X-Powered-By", "WebHandler API");
            return chain.filter(exchange);
        };
        HttpHandler webHttpHandler = WebHttpHandlerBuilder.webHandler(webHandler).filter(poweredBy).build();

        DisposableServer server1 = HttpServer.create().host("localhost").port(8081)
                .handle(new ReactorHttpHandlerAdapter(rawHandler)).bindNow();
        DisposableServer server2 = HttpServer.create().host("localhost").port(8082)
                .handle(new ReactorHttpHandlerAdapter(webHttpHandler)).bindNow();

        System.out.println("HttpHandler en http://localhost:8081  |  WebHandler en http://localhost:8082");
        System.out.println("Ctrl+C para parar");
        server1.onDispose().and(server2.onDispose()).block(); // block() aquí sí: es el main, no un hilo de eventos
    }
}
