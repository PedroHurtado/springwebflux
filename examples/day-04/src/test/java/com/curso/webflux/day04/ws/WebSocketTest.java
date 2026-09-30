package com.curso.webflux.day04.ws;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

/**
 * WebSockets contra el servidor real con el cliente WebSocket reactivo de WebFlux.
 *
 * WebTestClient no habla WebSocket: se usa un WebSocketClient (ReactorNettyWebSocketClient). Su método
 * execute(uri, handler) abre la conexión y ejecuta, EN EL CLIENTE, un WebSocketHandler como los del
 * servidor: la misma API (session.send / session.receive) a ambos lados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketTest {

    @LocalServerPort
    int port;

    final WebSocketClient client = new ReactorNettyWebSocketClient();

    @Test
    void echoReturnsEveryMessage() {
        Sinks.Many<String> received = Sinks.many().replay().all();

        Mono<Void> conversation = client.execute(uri("/ws/echo"), session -> session
                .send(Flux.just("hola", "adiós").map(session::textMessage))
                .thenMany(session.receive().map(WebSocketMessage::getPayloadAsText).take(2))
                .doOnNext(received::tryEmitNext)
                .then());                                        // al terminar el handler, el cliente cierra

        StepVerifier.create(conversation).expectComplete().verify(Duration.ofSeconds(5));
        StepVerifier.create(received.asFlux().take(2))
                .expectNext("eco: hola", "eco: adiós")
                .verifyComplete();
    }

    /** Sin comando no llega nada; tras {"productIds":[]} llegan los cambios de precio del ticker (1 por segundo). */
    @Test
    void pricesArePushedAfterAWatchCommand() {
        Queue<String> received = new ConcurrentLinkedQueue<>();

        Mono<Void> conversation = client.execute(uri("/ws/prices"), session -> session
                .send(Mono.just(session.textMessage("{\"productIds\":[]}")))
                .thenMany(session.receive().map(WebSocketMessage::getPayloadAsText).take(2))
                .doOnNext(received::add)
                .then());

        StepVerifier.create(conversation).expectComplete().verify(Duration.ofSeconds(6));
        assertThat(received).hasSize(2).allSatisfy(json -> assertThat(json).contains("\"productId\"", "\"newPrice\""));
    }

    /** Lo que escribe Ana le llega a Luis: difusión entre sesiones con un Sinks.Many. */
    @Test
    void chatBroadcastsToEverySession() {
        Sinks.One<String> luisReceived = Sinks.one();
        Sinks.Empty<Void> luisConnected = Sinks.empty();

        Mono<Void> luis = client.execute(uri("/ws/chat?name=Luis"), session -> {
            luisConnected.tryEmitEmpty();
            return session.receive().map(WebSocketMessage::getPayloadAsText).next()
                    .doOnNext(luisReceived::tryEmitValue)
                    .then();
        });
        Mono<Void> ana = client.execute(uri("/ws/chat?name=Ana"), session -> session
                .send(Mono.just(session.textMessage("hola a todos")))
                .then(Mono.delay(Duration.ofMillis(200)))       // dar tiempo al envío antes de cerrar
                .then());

        // Luis se conecta primero (directBestEffort no guarda mensajes para quien llega tarde)
        StepVerifier.create(Mono.when(luis, luisConnected.asMono().delayElement(Duration.ofMillis(200)).then(ana)))
                .expectComplete()
                .verify(Duration.ofSeconds(5));
        StepVerifier.create(luisReceived.asMono()).expectNext("Ana: hola a todos").verifyComplete();
    }

    /** Sin handler en esa URL el handshake recibe un 404 y la conexión no llega a abrirse. */
    @Test
    void unknownPathFailsTheHandshake() {
        StepVerifier.create(client.execute(uri("/ws/no-existe"), session -> Mono.empty()))
                .expectErrorMatches(error -> error.getMessage().contains("404"))
                .verify(Duration.ofSeconds(5));
    }

    private URI uri(String path) {
        return URI.create("ws://localhost:" + port + path);
    }
}
