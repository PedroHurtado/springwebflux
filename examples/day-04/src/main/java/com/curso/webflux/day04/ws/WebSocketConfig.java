package com.curso.webflux.day04.ws;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;

/**
 * Día 4 — WebSockets en WebFlux: no hay anotaciones (@MessageMapping es de Spring MVC + STOMP o de RSocket).
 * Cada URL se asocia a un WebSocketHandler mediante un HandlerMapping, igual que el DispatcherHandler
 * asocia rutas a controladores:
 *
 *   DispatcherHandler ─► SimpleUrlHandlerMapping (/ws/**) ─► WebSocketHandlerAdapter ─► handler.handle(session)
 *
 * WebSocketHandlerAdapter ya lo declara la configuración de WebFlux: solo hay que registrar el mapping.
 * Orden -1: se consulta ANTES que el de los controladores anotados (orden 0).
 */
@Configuration
public class WebSocketConfig {

    @Bean
    public HandlerMapping webSocketMapping(EchoWebSocketHandler echo, PriceWebSocketHandler prices,
                                           ChatWebSocketHandler chat) {
        return new SimpleUrlHandlerMapping(Map.of(
                "/ws/echo", echo,
                "/ws/prices", prices,
                "/ws/chat", chat), -1);
    }
}
