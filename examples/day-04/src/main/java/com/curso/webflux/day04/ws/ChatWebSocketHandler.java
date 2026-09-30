package com.curso.webflux.day04.ws;

import java.time.Duration;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Chat: lo que escribe un cliente les llega a TODOS los conectados (difusión entre sesiones).
 *
 * El punto de encuentro es un Sinks.Many: un publisher CALIENTE en el que se puede emitir desde fuera.
 *   - multicast(): varios suscriptores (una suscripción por sesión abierta);
 *   - directBestEffort(): sin buffer; si un cliente va lento, SOLO él pierde mensajes (no frena al resto).
 *
 * Aquí entrada y salida son INDEPENDIENTES (lo que envío no depende de lo que recibo en MI sesión), así que
 * se combinan con Mono.zip(input, output).then(), el patrón de la documentación de Spring.
 */
@Component
public class ChatWebSocketHandler implements WebSocketHandler {

    /**
     * Varias sesiones pueden emitir A LA VEZ (cada una en su hilo del event loop). tryEmitNext fallaría
     * con FAIL_NON_SERIALIZED; busyLooping reintenta durante un tiempo máximo hasta conseguir emitir.
     */
    private static final Sinks.EmitFailureHandler RETRY_ON_CONTENTION = Sinks.EmitFailureHandler.busyLooping(
            Duration.ofMillis(100));

    private final Sinks.Many<String> messages = Sinks.many().multicast().directBestEffort();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String name = nameOf(session);

        Mono<Void> input = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(text -> messages.emitNext(name + ": " + text, RETRY_ON_CONTENTION))
                .then();

        Mono<Void> output = session.send(messages.asFlux().map(session::textMessage));

        return Mono.zip(input, output).then();
    }

    /** ws://localhost:8080/ws/chat?name=Ana. Los datos del handshake (URI, cabeceras) están en HandshakeInfo. */
    private static String nameOf(WebSocketSession session) {
        String name = UriComponentsBuilder.fromUri(session.getHandshakeInfo().getUri()).build()
                .getQueryParams().getFirst("name");
        return name == null || name.isBlank() ? "anónimo" : name;
    }
}
