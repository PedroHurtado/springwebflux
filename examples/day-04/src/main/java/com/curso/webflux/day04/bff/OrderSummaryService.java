package com.curso.webflux.day04.bff;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.curso.webflux.day04.client.CatalogClient;
import com.curso.webflux.day04.client.OrderDto;
import com.curso.webflux.day04.client.OrderDto.LineDto;
import com.curso.webflux.day04.client.OrdersApi;
import com.curso.webflux.day04.orders.OrderNotFoundException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * BFF (Backend For Frontend): compone una respuesta llamando a DOS microservicios por HTTP.
 *
 *   cliente ──► BFF ──① GET /api/orders/{id}──────────► pedidos   (1 llamada)
 *                   └─② GET /api/products/{productId}──► catálogo  (N llamadas, EN PARALELO)
 *
 * Reglas para no romper la reactividad en una cadena de llamadas (docs/day-03/03-webclient.md):
 *   - Cada llamada remota devuelve Mono/Flux y se COMPONE con operadores (flatMap), nunca block().
 *   - Lo que depende de una respuesta anterior va dentro del flatMap (② necesita los productId de ①).
 *   - Lo que es independiente va en paralelo (flatMap/flatMapSequential con concurrencia limitada).
 *   - Los fallos parciales se convierten en valores (onErrorResume) para degradar, no para romper.
 *   - El método devuelve el pipeline: la suscripción la hace WebFlux al escribir la respuesta.
 */
@Service
public class OrderSummaryService {

    private static final Logger log = LoggerFactory.getLogger(OrderSummaryService.class);

    /** Máximo de llamadas simultáneas al catálogo por pedido: paralelismo SIN saturar al otro servicio. */
    static final int CONCURRENCY = 8;

    private final OrdersApi orders;
    private final CatalogClient catalog;

    public OrderSummaryService(OrdersApi orders, CatalogClient catalog) {
        this.orders = orders;
        this.catalog = catalog;
    }

    /**
     * Resumen completo (un único JSON). Tiempo total ≈ latencia(pedidos) + max(latencia de cada producto),
     * no la suma: las N llamadas al catálogo se hacen a la vez.
     */
    public Mono<OrderSummary> summary(String orderId) {
        return findOrder(orderId)                                             // ① Mono<OrderDto>
                .flatMap(order -> Flux.fromIterable(order.details())
                        .flatMapSequential(this::enrich, CONCURRENCY)         // ② en paralelo, resultados EN ORDEN
                        .reduce(OrderSummary.start(order), OrderSummary::add)); // ③ acumular (sin collectList)
    }

    /**
     * Las mismas líneas en STREAMING (NDJSON): cada línea se envía al cliente en cuanto responde el
     * catálogo (flatMap: orden de llegada). Nada se acumula en memoria en ningún punto de la cadena.
     */
    public Flux<LineView> lines(String orderId) {
        return findOrder(orderId)
                .flatMapMany(order -> Flux.fromIterable(order.details()))
                .flatMap(this::enrich, CONCURRENCY);
    }

    /**
     * ❌ ANTI-PATRÓN, solo para verlo fallar: el mismo resumen escrito "como en Spring MVC".
     *
     * block() suscribe y ESPERA en el hilo actual. Aquí el hilo actual es un hilo del event loop de
     * Reactor Netty (reactor-http-nio-N; en los tests, webflux-http-nio-N), así que Reactor se niega a esperar:
     *   IllegalStateException: block()/blockFirst()/blockLast() are blocking, which is not supported
     *   in thread reactor-http-nio-3
     * Si no lo impidiera, un puñado de peticiones lentas dejaría el servidor sin hilos para atender
     * a nadie (y, si el servicio se llama a sí mismo, podría quedarse esperando para siempre).
     */
    public Mono<OrderSummary> summaryBlocking(String orderId) {
        log.warn("summaryBlocking en el hilo {}: block() aquí es un error", Thread.currentThread().getName());
        OrderDto order = findOrder(orderId).block();                        // 💥 en un hilo del event loop
        OrderSummary summary = OrderSummary.start(order);
        for (LineDto line : order.details()) {                               // y además en SERIE: N x latencia
            summary = summary.add(enrich(line).block());
        }
        return Mono.just(summary);
    }

    /** Un 404 del microservicio de pedidos se traduce a la excepción de NUESTRA API (ProblemDetail 404). */
    private Mono<OrderDto> findOrder(String orderId) {
        return orders.findById(orderId)
                .onErrorMap(WebClientResponseException.NotFound.class, e -> new OrderNotFoundException(orderId));
    }

    /**
     * Enriquece una línea con el estado actual del producto. NUNCA falla:
     *   producto encontrado  -> AVAILABLE / OUT_OF_STOCK
     *   404 (Mono vacío)     -> DISCONTINUED
     *   timeout / 5xx tras los reintentos de CatalogClient -> UNKNOWN (degradación, no error)
     */
    private Mono<LineView> enrich(LineDto line) {
        return catalog.findProduct(line.productId())
                .map(product -> LineView.of(line, product))
                .defaultIfEmpty(LineView.discontinued(line))
                .onErrorResume(error -> {
                    log.warn("Catálogo no disponible para {}: {}", line.productId(), error.toString());
                    return Mono.just(LineView.unknown(line));
                });
    }
}
