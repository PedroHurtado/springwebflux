package com.curso.webflux.day04.ws;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import com.curso.webflux.day04.catalog.PriceFeed;
import com.curso.webflux.day04.catalog.ProductService.PriceChange;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Precios en tiempo real por WebSocket, con lo que SSE no puede hacer: el cliente ENVÍA comandos por la
 * misma conexión para cambiar qué productos quiere vigilar.
 *
 *   cliente ─► {"productIds":["1","3"]}      solo cambios de los productos 1 y 3
 *   cliente ─► {"productIds":[]}             todos los productos
 *   servidor ─► {"productId":"3","name":"Monitor 27 pulgadas","oldPrice":249.00,"newPrice":251.30}
 *
 * WebSocket no define formato de mensaje: aquí se usa JSON con el JsonMapper (Jackson 3) de Spring Boot.
 */
@Component
public class PriceWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PriceWebSocketHandler.class);

    private final PriceFeed feed;
    private final JsonMapper json;

    public PriceWebSocketHandler(PriceFeed feed, JsonMapper json) {
        this.feed = feed;
        this.json = json;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        Flux<Set<String>> commands = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .handle(this::parse);                                  // comandos no válidos: se ignoran

        Flux<WebSocketMessage> output = watch(commands, feed.changes())
                .map(json::writeValueAsString)                         // Jackson 3: excepciones no comprobadas
                .map(session::textMessage)
                .takeUntilOther(session.closeStatus());                // al cerrar: dejar de escuchar el ticker

        return session.send(output);
    }

    /**
     * La lógica, separada de WebSocket para poder probarla con StepVerifier y TestPublisher
     * (ver testing/StepVerifierAdvancedTest).
     *
     * switchMap: con cada comando nuevo se CANCELA la suscripción anterior al feed y se abre otra con el
     * nuevo filtro. No hay estado mutable (ni un Set compartido que proteger): el "estado" es el último comando.
     * Hasta el primer comando no se envía nada.
     */
    static Flux<PriceChange> watch(Flux<Set<String>> commands, Flux<PriceChange> changes) {
        return commands.switchMap(ids -> changes.filter(change -> ids.isEmpty() || ids.contains(change.productId())));
    }

    private void parse(String text, SynchronousSink<Set<String>> sink) {
        try {
            WatchCommand command = json.readValue(text, WatchCommand.class);
            sink.next(command.productIds() == null ? Set.of() : Set.copyOf(command.productIds()));
        }
        catch (JacksonException ex) {
            log.warn("Comando WebSocket no válido: {}", text);
        }
    }

    /** Mensaje del cliente. */
    record WatchCommand(List<String> productIds) {
    }
}
