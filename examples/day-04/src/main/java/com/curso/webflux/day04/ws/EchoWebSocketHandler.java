package com.curso.webflux.day04.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import reactor.core.publisher.Mono;

/**
 * El WebSocketHandler más sencillo: devuelve cada mensaje recibido.
 *
 * Una conexión WebSocket son DOS flujos independientes:
 *   session.receive()      Flux<WebSocketMessage>  lo que envía el cliente (termina cuando cierra)
 *   session.send(Publisher)  Mono<Void>            lo que enviamos nosotros
 * El Mono<Void> que devuelve handle() marca la vida de la sesión: cuando termina, se cierra la conexión.
 *
 * Aquí la salida SE CONSTRUYE a partir de la entrada (map), así que basta con devolver session.send(...):
 * cuando el cliente cierra, receive() completa, la salida completa y la sesión termina.
 */
@Component
public class EchoWebSocketHandler implements WebSocketHandler {

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return session.send(session.receive()
                .map(WebSocketMessage::getPayloadAsText)          // leer el texto AQUÍ: el buffer se libera después
                .map(text -> session.textMessage("eco: " + text)));
    }
}
