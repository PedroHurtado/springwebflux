# 6. Laboratorio del día 4

> Proyecto: `examples/day-04` · Duración: 60 min repartidos entre los bloques + proyecto integrador (para casa o
> para el final de la sesión)
>
> Todas las soluciones se han compilado y ejecutado contra una copia del proyecto del día (Spring Boot 4.1.1) con
> tests de integración (`WebTestClient`, `ReactorNettyWebSocketClient`), `@DataR2dbcTest`, `@WebFluxTest` o
> `StepVerifier`.

## Parte 0 — Arranque (5 min)

```bash
cd examples/day-04
./mvnw test              # Windows: mvnw.cmd test   → 93 tests + 4 de BlockHound
./mvnw spring-boot:run
```

1. En el log del arranque, busca las sentencias de `schema.sql`. Después lanza `GET /api/products/3` dos veces y
   anota el `ETag`. Abre <http://localhost:8080> (el ticker de precios empieza) y repite: ¿qué ha cambiado y por qué?
2. Abre <http://localhost:8080/catalog/live> con DevTools → *Network*. ¿Cuánto tarda la petición? ¿Cuándo aparece el
   título de la página?

<details><summary>Respuestas</summary>

1. El `ETag` pasa de `"3-v0"` a `"3-vN"`: es la columna `version` (`@Version`) y el ticker hace
   `UPDATE product SET price = ..., version = version + 1` cada vez que cambia un precio (el del producto 3 cambia
   cuando el ticker lo elige al azar).
2. Unos 2 s (5 productos × 400 ms), pero el título aparece enseguida: modo *data-driven*, la página se envía por
   trozos según llegan los productos.
</details>

## Parte A — Acceso a datos (15 min, tras el bloque 1)

### A1. Productos con poco stock desde la base de datos

Añade `GET /api/products/low-stock?max=10` que devuelva los productos con `stock < max`, ordenados por stock
ascendente. El filtro y el orden los debe hacer la **base de datos** (consulta derivada). `max` debe ser ≥ 1 (si no,
400). Escribe un `@DataR2dbcTest` para la consulta.

<details><summary>Solución</summary>

```java
// catalog/ProductRepository.java — consulta derivada: WHERE STOCK < $1 ORDER BY STOCK
Flux<Product> findByStockLessThanOrderByStock(int max);

// catalog/ProductService.java
public Flux<Product> lowStock(int max) {
    return repository.findByStockLessThanOrderByStock(max);
}

// catalog/ProductController.java (antes de "/{id}" no hace falta: una ruta literal gana a una con variable)
@GetMapping("/low-stock")
public Flux<Product> lowStock(@RequestParam(defaultValue = "10") @Min(1) int max) {
    return service.lowStock(max);
}
```

```java
@DataR2dbcTest
class LowStockRepositoryTest {

    @Autowired
    ProductRepository repository;

    @Test
    void productsWithLessStockThanTheLimitOrderedByStock() {
        StepVerifier.create(repository.findByStockLessThanOrderByStock(20).map(Product::id))
                .expectNext("4", "3")          // 5 y 10 unidades (otros tests pueden añadir productos detrás)
                .thenCancel()
                .verify();
    }
}
```

`thenCancel()` y no `verifyComplete()`: `@DataR2dbcTest` comparte la base de datos con `ProductRepositoryTest`
(mismo contexto) y no hace *rollback*, así que puede haber más productos detrás.
</details>

### A2. Romper la transacción (sin código)

Un compañero cambia `reserveStockAndSave` así, "para no hacer esperar al cliente mientras se descuenta el stock":

```java
private Mono<Order> reserveStockAndSave(Order order) {
    Flux.fromIterable(order.details())
            .concatMap(detail -> products.reserveStock(detail.productId(), detail.quantity()))
            .subscribe();                                             // "en segundo plano"
    return orders.save(order).as(transactions::transactional);
}
```

¿Qué problemas tiene? Busca al menos tres.

<details><summary>Respuesta</summary>

1. **Fuera de la transacción**: el `subscribe()` crea una suscripción nueva, sin el `Context` en el que
   `TransactionalOperator` guarda la conexión transaccional. Las reservas usan otra conexión y no se deshacen si
   falla el `INSERT`.
2. **Se pierde el resultado**: si una reserva devuelve 0 filas (sin stock), nadie se entera; el pedido se guarda
   igual. Adiós a la protección contra la sobreventa.
3. **Se pierden los errores**: un fallo en la reserva no llega al cliente (y `subscribe()` sin manejador de error
   solo lo registra en el log).
4. **Sin *backpressure* ni cancelación**: si el cliente cancela, las reservas siguen.
5. No "ahorra" nada: el hilo no espera en ninguno de los dos casos; en la versión correcta tampoco se bloquea.
</details>

## Parte B — Vistas (10 min, tras el bloque 2)

### B1. Página de pedidos de un cliente

Crea la página `GET /customers/{customerId}/orders` que muestre los pedidos del cliente (fecha, líneas y total) en
modo *data-driven*.

<details><summary>Solución</summary>

```java
// web/OrderViewController.java
@Controller
public class OrderViewController {

    private final OrderService service;

    public OrderViewController(OrderService service) {
        this.service = service;
    }

    @GetMapping("/customers/{customerId}/orders")
    public String orders(@PathVariable String customerId, Model model) {
        model.addAttribute("customerId", customerId);
        model.addAttribute("orders", new ReactiveDataDriverContextVariable(service.findByCustomer(customerId), 1));
        return "orders/list";
    }
}
```

```html
<!-- templates/orders/list.html -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="es">
<head th:replace="~{fragments :: head('Pedidos')}"></head>
<body>
<nav th:replace="~{fragments :: nav}"></nav>
<h1>Pedidos de <span th:text="${customerId}">cliente-1</span></h1>
<section th:each="order : ${orders}">
  <h2 th:text="'Pedido ' + ${order.id}">Pedido</h2>
  <p th:text="${#temporals.format(order.createdAt, 'dd/MM/yyyy HH:mm')}">fecha</p>
  <table>
    <tr th:each="line : ${order.details}">
      <td th:text="${line.productName}">Teclado</td>
      <td class="num" th:text="${line.quantity}">1</td>
      <td class="num" th:text="${line.subtotal}">89.90</td>
    </tr>
    <tr><th colspan="2">Total</th><th class="num" th:text="${order.total}">89.90</th></tr>
  </table>
</section>
</body>
</html>
```

Solo el `th:each` exterior es *data-driven*; `order.details` ya es una `List` dentro de cada `Order` (el agregado
llega completo de `OrderRepository`).
</details>

## Parte C — WebSockets (10 min, tras el bloque 3)

### C1. Avisos de entrada y salida en el chat

Cuando alguien entra en el chat, los demás deben recibir `"Ana se ha unido"`, y al salir (cierre normal, error o
cancelación), `"Ana ha salido"`. Sin estado mutable. Ojo: `WebSocketTest.chatBroadcastsToEverySession` espera que el
primer mensaje que recibe Luis sea el de Ana; tendrás que adaptarlo.

<details><summary>Solución</summary>

```java
// ws/ChatWebSocketHandler.java
Mono<Void> input = session.receive()
        .map(WebSocketMessage::getPayloadAsText)
        .doOnSubscribe(subscription -> publish(name + " se ha unido"))
        .doOnNext(text -> publish(name + ": " + text))
        .doFinally(signal -> publish(name + " ha salido"))    // cierre normal, error o cancelación
        .then();

private void publish(String message) {
    messages.emitNext(message, RETRY_ON_CONTENTION);
}
```

```java
// WebSocketTest.chatBroadcastsToEverySession: Luis ignora los avisos
return session.receive().map(WebSocketMessage::getPayloadAsText)
        .filter(text -> text.startsWith("Ana:"))
        .next()
        ...
```

Test de la solución (Luis recibe los tres mensajes en orden):

```java
StepVerifier.create(seen.asFlux().take(3))
        .expectNext("Ana se ha unido", "Ana: hola", "Ana ha salido")
        .verifyComplete();
```

`doFinally` es el sitio correcto para "salir": se ejecuta con cualquier señal de terminación (`onComplete`,
`onError`, `cancel`). Quien entra no ve su propio aviso: `Mono.zip` se suscribe a `input` antes que a `output`.
</details>

## Parte D — Pruebas (15 min, tras el bloque 4)

### D1. *Slice* web del BFF

Escribe un `@WebFluxTest(OrderSummaryController.class)` con `@MockitoBean OrderSummaryService` que compruebe:
(a) un pedido inexistente → 404 `application/problem+json` con `orderId`; (b) `/lines` devuelve NDJSON.

<details><summary>Solución</summary>

```java
@WebFluxTest(OrderSummaryController.class)
class OrderSummaryControllerSliceTest {

    @Autowired
    WebTestClient client;

    @MockitoBean
    OrderSummaryService service;

    @Test
    void unknownOrderIs404ProblemDetail() {
        when(service.summary("x")).thenReturn(Mono.error(new OrderNotFoundException("x")));

        client.get().uri("/api/bff/orders/x")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.orderId").isEqualTo("x");
    }

    @Test
    void linesAreStreamedAsNdjson() {
        when(service.lines("o-1")).thenReturn(Flux.just(
                new LineView("1", "Teclado", 1, BigDecimal.TEN, BigDecimal.TEN, LineView.Status.AVAILABLE),
                new LineView("2", "Ratón", 2, BigDecimal.ONE, null, LineView.Status.UNKNOWN)));

        client.get().uri("/api/bff/orders/o-1/lines")
                .accept(MediaType.APPLICATION_NDJSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
                .expectBodyList(LineView.class).hasSize(2);
    }
}
```

Sin `FakeRemoteServices` ni `WebClient`: el *slice* no arranca los clientes HTTP (`ClientConfig` es una
`@Configuration` propia y no entra en `@WebFluxTest`).
</details>

### D2. `PublisherProbe`: borrar solo lo que existe

Con Mockito y `PublisherProbe`, comprueba que `ProductService.delete("x")` de un producto inexistente devuelve
`ProductNotFoundException` y **no** ejecuta el borrado, y que con uno existente sí lo ejecuta.

<details><summary>Solución</summary>

```java
class ProductServiceDeleteTest {

    ProductRepository repository = mock(ProductRepository.class);
    ProductService service = new ProductService(repository);

    @Test
    void deleteOfAnUnknownProductNeverDeletes() {
        PublisherProbe<Void> delete = PublisherProbe.empty();
        when(repository.findById("x")).thenReturn(Mono.empty());
        when(repository.delete(any())).thenReturn(delete.mono());

        StepVerifier.create(service.delete("x"))
                .expectError(ProductNotFoundException.class)
                .verify();

        delete.assertWasNotSubscribed();
    }

    @Test
    void deleteOfAnExistingProductDeletesIt() {
        PublisherProbe<Void> delete = PublisherProbe.empty();
        when(repository.findById("1")).thenReturn(Mono.just(new Product("1", "A", "c", BigDecimal.ONE, 1, 0L)));
        when(repository.delete(any())).thenReturn(delete.mono());

        StepVerifier.create(service.delete("1")).verifyComplete();

        delete.assertWasSubscribed();
    }
}
```
</details>

## Parte E — Otras bibliotecas reactivas (5 min, tras el bloque 5)

### E1. Cuerpo de la petición con RxJava

Añade `POST /api/interop/rx/products` que reciba un `Single<ProductRequest>` validado con `@Valid` y devuelva un
`Single<Product>` con estado 201. Comprueba que un cuerpo no válido da 400.

<details><summary>Solución</summary>

```java
@PostMapping("/rx/products")
@ResponseStatus(HttpStatus.CREATED)
public Single<Product> create(@Valid @RequestBody Single<ProductRequest> request) {
    return request.flatMap(body -> Single.fromPublisher(service.create(body)));
}
```

WebFlux decodifica el cuerpo como un `Mono`, lo valida y lo adapta a `Single` con el `ReactiveAdapterRegistry`; la
respuesta hace el camino inverso. La validación funciona igual que con `Mono<ProductRequest>`: el error llega como
señal de error del `Single` y el `@RestControllerAdvice` lo convierte en 400.
</details>

## Proyecto integrador — Reseñas de producto (60–90 min)

Un ejercicio que recorre **todo** el curso sobre el mismo dominio. Los clientes pueden valorar productos (1 a 5
estrellas y un comentario).

| # | Requisito | Temas |
|---|---|---|
| 1 | Tabla `review` en H2 y repositorio de Spring Data R2DBC | R2DBC |
| 2 | `POST /api/products/{id}/reviews` → 201 + `Location`; 400 si la puntuación no está entre 1 y 5; 404 si el producto no existe (`ProblemDetail` con `instance`) | Endpoints **funcionales**, validación manual, errores, URIs |
| 3 | `GET /api/products/{id}/reviews` (JSON o NDJSON) y `GET /api/products/{id}/reviews/summary` (`count` y `average`) **sin `collectList`** | Reactor (`reduce`), *streaming* |
| 4 | `GET /api/products/{id}/reviews/live`: SSE con cada reseña nueva de ese producto | Publicador caliente (`Sinks`), SSE |
| 5 | La ficha `/catalog/{id}` muestra la valoración media | Vistas, modelo reactivo |
| 6 | Tests de todo lo anterior | Pruebas |

Pistas: `Review` como `record` con `@Version` (id asignado por la aplicación); `ProductNotFoundException` **no** es
un `ErrorResponse`, así que en el `RouterFunction` necesita su propio `onError`.

<details><summary>Solución</summary>

```sql
-- schema.sql
CREATE TABLE review (
    id         VARCHAR(36)              PRIMARY KEY,
    product_id VARCHAR(36)              NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    rating     INT                      NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment    VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version    BIGINT                   NOT NULL
);
```

```java
// reviews/Review.java — version null -> save() hace INSERT aunque el id lo asigne la aplicación
@Table
public record Review(@Id String id, String productId, int rating, String comment, Instant createdAt,
                     @Version @JsonIgnore Long version) {
}

// reviews/ReviewRequest.java
public record ReviewRequest(@Min(1) @Max(5) int rating, @Size(max = 500) String comment) {
}

// reviews/ReviewRepository.java
public interface ReviewRepository extends ReactiveCrudRepository<Review, String> {
    Flux<Review> findByProductIdOrderByCreatedAtDesc(String productId);
}

// reviews/RatingSummary.java — acumulador inmutable del reduce
public record RatingSummary(long count, long total) {

    public static final RatingSummary EMPTY = new RatingSummary(0, 0);

    public RatingSummary add(Review review) {
        return new RatingSummary(count + 1, total + review.rating());
    }

    /** Media con un decimal; null si no hay reseñas. Jackson la publica como "average". */
    public BigDecimal getAverage() {
        return count == 0 ? null
                : BigDecimal.valueOf(total).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }
}
```

```java
// reviews/ReviewService.java
@Service
public class ReviewService {

    private static final Sinks.EmitFailureHandler RETRY = Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100));

    private final ReviewRepository reviews;
    private final ProductService products;
    /** Reseñas nuevas en caliente (SSE /live). Sin buffer: quien no está conectado no las recibe. */
    private final Sinks.Many<Review> published = Sinks.many().multicast().directBestEffort();

    public ReviewService(ReviewRepository reviews, ProductService products) {
        this.reviews = reviews;
        this.products = products;
    }

    public Mono<Review> add(String productId, ReviewRequest request) {
        return products.findById(productId)                                    // 404 si no existe
                .flatMap(product -> reviews.save(new Review(UUID.randomUUID().toString(), productId,
                        request.rating(), request.comment(), Instant.now().truncatedTo(ChronoUnit.MILLIS), null)))
                .doOnNext(review -> published.emitNext(review, RETRY));        // solo si se guardó
    }

    public Flux<Review> findByProduct(String productId) {
        return products.findById(productId)
                .thenMany(reviews.findByProductIdOrderByCreatedAtDesc(productId));
    }

    /** Sin collectList: cada reseña se suma al acumulador según llega de la BD. */
    public Mono<RatingSummary> summary(String productId) {
        return findByProduct(productId).reduce(RatingSummary.EMPTY, RatingSummary::add);
    }

    public Flux<Review> live(String productId) {
        return products.findById(productId)
                .thenMany(published.asFlux().filter(review -> review.productId().equals(productId)));
    }
}
```

```java
// reviews/ReviewRouter.java
@Configuration
public class ReviewRouter {

    @Bean
    public RouterFunction<ServerResponse> reviewRoutes(ReviewService service, Validator validator) {
        return route()
                .path("/api/products/{id}/reviews", reviews -> reviews
                        .GET("/summary", request -> ServerResponse.ok()
                                .body(service.summary(request.pathVariable("id")), RatingSummary.class))
                        .GET("/live", request -> ServerResponse.ok()
                                .contentType(MediaType.TEXT_EVENT_STREAM)
                                .body(live(service, request.pathVariable("id")), ServerSentEvent.class))
                        .GET(request -> ServerResponse.ok()
                                .body(service.findByProduct(request.pathVariable("id")), Review.class))
                        .POST(contentType(MediaType.APPLICATION_JSON), request -> create(request, service, validator)))
                // ProductNotFoundException no es un ErrorResponse: el @RestControllerAdvice no se aplica aquí
                .onError(ProductNotFoundException.class, (error, request) -> problem(Problems.of(HttpStatus.NOT_FOUND,
                        "product-not-found", "Producto no encontrado", error.getMessage()), request))
                .onError(ServerWebInputException.class, (error, request) -> problem(
                        ((ServerWebInputException) error).getBody(), request))
                .build();
    }

    /**
     * Un comentario SSE inicial (": conectado"): la respuesta no se envía hasta el primer dato, así que sin él
     * el cliente se quedaría esperando las cabeceras hasta la primera reseña.
     */
    private static Flux<ServerSentEvent<Review>> live(ReviewService service, String productId) {
        return Flux.concat(Mono.just(ServerSentEvent.<Review>builder().comment("conectado").build()),
                service.live(productId).map(review -> ServerSentEvent.builder(review).event("review").build()));
    }

    private static Mono<ServerResponse> create(ServerRequest request, ReviewService service, Validator validator) {
        String productId = request.pathVariable("id");
        return request.bodyToMono(ReviewRequest.class)
                .switchIfEmpty(Mono.error(() -> new ServerWebInputException("El cuerpo es obligatorio")))
                .flatMap(body -> validate(body, validator))
                .flatMap(body -> service.add(productId, body))
                .flatMap(review -> ServerResponse.created(request.uriBuilder().path("/{reviewId}").build(review.id()))
                        .bodyValue(review));
    }

    private static Mono<ReviewRequest> validate(ReviewRequest body, Validator validator) {
        Errors errors = new DirectFieldBindingResult(body, "review");
        validator.validate(body, errors);
        return errors.hasErrors()
                ? Mono.error(new ServerWebInputException("Reseña no válida: " + errors.getFieldErrors().stream()
                        .map(error -> error.getField() + " " + error.getDefaultMessage()).toList()))
                : Mono.just(body);
    }

    private static Mono<ServerResponse> problem(ProblemDetail problem, ServerRequest request) {
        problem.setInstance(URI.create(request.path()));
        return ServerResponse.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(problem);
    }
}
```

```java
// web/CatalogViewController.detail — el Mono<RatingSummary> se resuelve antes de renderizar
.modelAttribute("rating", reviews.summary(id))
```

```html
<!-- templates/catalog/detail.html -->
<tr><th>Valoración</th>
    <td th:text="${rating.count == 0} ? 'Sin reseñas' : '★ ' + ${rating.average} + ' (' + ${rating.count} + ' reseñas)'">★ 4,5 (2 reseñas)</td></tr>
```

Test (resumen de lo que se verificó):

```java
@Test
void integratorReviews() {
    var live = client.get().uri("/api/products/3/reviews/live")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(Review.class)
            .getResponseBody();

    StepVerifier.create(live.take(1))
            .then(() -> {
                postReview("3", 5, "Muy bueno").expectStatus().isCreated()
                        .expectHeader().valueMatches("Location", ".*/api/products/3/reviews/.+");
                postReview("3", 4, null).expectStatus().isCreated();
            })
            .assertNext(review -> assertThat(review.rating()).isEqualTo(5))
            .verifyComplete();

    client.get().uri("/api/products/3/reviews/summary").exchange()
            .expectBody().jsonPath("$.count").isEqualTo(2).jsonPath("$.average").isEqualTo(4.5);
    postReview("3", 6, "demasiado").expectStatus().isBadRequest();
    postReview("no-existe", 3, "x").expectStatus().isNotFound()
            .expectBody().jsonPath("$.instance").isEqualTo("/api/products/no-existe/reviews");
    client.get().uri("/catalog/3").exchange()
            .expectBody(String.class).value(html -> assertThat(html).contains("★ 4.5 (2 reseñas)"));
}
```

Detalles que conviene comentar al corregir:

- **SSE y cabeceras**: sin el comentario inicial, `exchange()` del test se quedaba esperando las cabeceras hasta
  agotar el tiempo (la respuesta no se envía hasta el primer elemento). En producción, además, conviene un
  *heartbeat* periódico (`Flux.interval` + comentario) para que los proxies no corten la conexión.
- **`summary` con `reduce`**: el mismo patrón del acumulador inmutable del día 2 (`OrderValidation`) y del día 3
  (`OrderSummary`).
- **`doOnNext` tras `save`**: se publica solo lo que se ha guardado. Con varias instancias, el `Sinks` es local a
  cada una (ver 3.5).
- **Rutas funcionales y anotadas conviven**: `/api/products/{id}/reviews` (funcional) junto a `/api/products/**`
  (anotado). El `RouterFunctionMapping` se consulta antes que el de los controladores.
</details>

⬅️ [Índice del día 4](README.md) · ➡️ [7. Cierre del curso](07-cierre-del-curso.md)
