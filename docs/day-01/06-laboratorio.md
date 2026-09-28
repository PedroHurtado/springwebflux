# 6. Laboratorio del día 1

> Proyecto: `examples/day-01` · Duración total aproximada: 60 min (repartidos entre los bloques)
>
> Todas las soluciones se han compilado y ejecutado contra el proyecto del día (Spring Boot 4.1.1).

## Parte 0 — Arranque (5 min)

```bash
cd examples/day-01
./mvnw test              # Windows: mvnw.cmd test   → 43 tests OK
./mvnw spring-boot:run
```

1. Abre <http://localhost:8080> y observa los precios en tiempo real (SSE).
2. En Chrome DevTools → *Network* → selecciona la petición `prices` → pestaña *EventStream*.
3. Abre <http://localhost:8080/internals/dispatcher> e identifica los tres tipos de beans especiales.

## Parte A — Reactor (20 min)

Crea la clase `src/test/java/com/curso/webflux/day01/reactor/LabReactorTest.java` y resuelve cada ejercicio
con un test que use `StepVerifier`.

### A1. Filtrar y transformar

Dado `Flux.just("ana", "pedro", "luis", "bo")`, devuelve en mayúsculas los nombres de más de 3 letras.
Resultado esperado: `"PEDRO", "LUIS"`.

<details><summary>Solución</summary>

```java
Flux<String> a1(Flux<String> names) {
    return names.filter(n -> n.length() > 3).map(String::toUpperCase);
}

@Test
void a1() {
    StepVerifier.create(a1(Flux.just("ana", "pedro", "luis", "bo")))
            .expectNext("PEDRO", "LUIS")
            .verifyComplete();
}
```
</details>

### A2. Llamadas en paralelo

Tienes dos "servicios" que tardan 100 ms cada uno. Combínalos para obtener `"precio-7|stock-7"` en
**menos de 180 ms** (es decir, en paralelo, no en secuencia).

```java
Mono<String> fetchPrice(String id) { return Mono.just("precio-" + id).delayElement(Duration.ofMillis(100)); }
Mono<String> fetchStock(String id) { return Mono.just("stock-" + id).delayElement(Duration.ofMillis(100)); }
```

<details><summary>Solución</summary>

```java
Mono<String> a2(String id) {
    return Mono.zip(fetchPrice(id), fetchStock(id))
            .map(t -> t.getT1() + "|" + t.getT2());
}

@Test
void a2() {
    StepVerifier.create(a2("7"))
            .expectNext("precio-7|stock-7")
            .expectComplete()
            .verify(Duration.ofMillis(180));   // falla si se ejecutan en serie (≈200 ms)
}
```
</details>

### A3. Timeout con valor por defecto

Un servicio no responde nunca (`Mono.never()`). Devuelve `"sin datos"` si no hay respuesta en 200 ms.

<details><summary>Solución</summary>

```java
Mono<String> a3(Mono<String> flaky) {
    return flaky.timeout(Duration.ofMillis(200)).onErrorReturn("sin datos");
}

@Test
void a3() {
    StepVerifier.create(a3(Mono.never())).expectNext("sin datos").verifyComplete();
}
```
</details>

### A4. Agrupar

Dado un `Flux<Product>`, obtén un `Mono<Map<String, Long>>` con el número de productos por categoría.

<details><summary>Solución (dos alternativas)</summary>

```java
// Con groupBy: cada grupo es un Flux; contamos cada uno y recogemos en un mapa
Mono<Map<String, Long>> a4(Flux<Product> products) {
    return products.groupBy(Product::category)
            .flatMap(group -> group.count().map(count -> Map.entry(group.key(), count)))
            .collectMap(Map.Entry::getKey, Map.Entry::getValue);
}

// Con collectMultimap + transformación final
Mono<Map<String, Long>> a4bis(Flux<Product> products) {
    return products.collectMultimap(Product::category)
            .map(m -> m.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> (long) e.getValue().size())));
}
```
</details>

### A5. Tiempo virtual

Verifica que `Flux.interval(Duration.ofSeconds(1)).take(3).map(i -> "tick-" + i)` emite
`tick-0, tick-1, tick-2` **sin esperar 3 segundos reales**.

<details><summary>Solución</summary>

```java
@Test
void a5() {
    StepVerifier.withVirtualTime(() -> Flux.interval(Duration.ofSeconds(1)).take(3).map(i -> "tick-" + i))
            .thenAwait(Duration.ofSeconds(3))
            .expectNext("tick-0", "tick-1", "tick-2")
            .verifyComplete();
}
```
</details>

## Parte B — Controlador reactivo (30 min)

Añade estos endpoints a `ProductController` (o a un controlador nuevo) y pruébalos con `curl`, el fichero
`requests.http` o un test con `WebTestClient`.

### B1. Filtro por precio

`GET /api/products/cheaper-than?max=60` → productos con precio ≤ `max`.

<details><summary>Solución</summary>

```java
@GetMapping("/cheaper-than")
public Flux<Product> cheaperThan(@RequestParam BigDecimal max) {
    return service.findAll(null).filter(p -> p.price().compareTo(max) <= 0);
}
```
Con los datos iniciales devuelve 2 productos (ratón y auriculares).
</details>

### B2. Resumen del catálogo

`GET /api/products/summary` → `{"products":5,"totalUnits":180,"stockValue":15587.50}`
(`stockValue` = Σ precio × stock).

<details><summary>Solución</summary>

```java
public record CatalogSummary(long products, int totalUnits, BigDecimal stockValue) { }

@GetMapping("/summary")
public Mono<CatalogSummary> summary() {
    return service.findAll(null)
            .reduce(new CatalogSummary(0, 0, BigDecimal.ZERO), (acc, p) -> new CatalogSummary(
                    acc.products() + 1,
                    acc.totalUnits() + p.stock(),
                    acc.stockValue().add(p.price().multiply(BigDecimal.valueOf(p.stock())))));
}
```
</details>

> ⚠️ Declara `/summary` y `/cheaper-than` como rutas literales: `PathPattern` prioriza las rutas más
> específicas, así que no chocan con `/{id}`.

### B3. SSE con nombre de evento e id

`GET /api/products/prices/events` → eventos SSE con `id` secuencial y `event` = `price-up` o `price-down`.
En el navegador: `source.addEventListener('price-up', e => ...)`.

<details><summary>Solución</summary>

```java
@GetMapping(path = "/prices/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<ProductService.PriceChange>> priceEvents() {
    return service.priceTicker()
            .index()                                   // Tuple2<Long, PriceChange>
            .map(t -> ServerSentEvent.builder(t.getT2())
                    .id(String.valueOf(t.getT1()))
                    .event(t.getT2().newPrice().compareTo(t.getT2().oldPrice()) >= 0 ? "price-up" : "price-down")
                    .build());
}
```

```text
id:0
event:price-up
data:{"productId":"2","name":"Ratón inalámbrico","oldPrice":29.95,"newPrice":31.24}
```
</details>

### B4. Importación masiva en *streaming* (NDJSON)

`POST /api/products/bulk` con `Content-Type: application/x-ndjson` → crea cada producto según llega y
devuelve los creados también como NDJSON.

```bash
curl -X POST http://localhost:8080/api/products/bulk \
  -H "Content-Type: application/x-ndjson" -H "Accept: application/x-ndjson" \
  --data-binary $'{"name":"A","category":"z","price":1,"stock":1}\n{"name":"B","category":"z","price":2,"stock":1}\n'
```

<details><summary>Solución</summary>

```java
@PostMapping(path = "/bulk",
             consumes = MediaType.APPLICATION_NDJSON_VALUE,
             produces = MediaType.APPLICATION_NDJSON_VALUE)
public Flux<Product> bulk(@RequestBody Flux<ProductRequest> requests) {
    return requests.concatMap(service::create);   // en orden, uno a uno
}
```
</details>

## Parte C — Observar el modelo de hilos (10 min)

1. Abre en **6 pestañas a la vez** `http://localhost:8080/api/demo/non-blocking?ms=5000`. Todas responden a
   los ~5 s. Mira `hiloPeticion` y `hiloRespuesta`.
2. Repite con `/api/demo/blocking?ms=5000`. ¿Qué pasa con las pestañas que comparten hilo
   `reactor-http-nio-N`? ¿Y si mientras tanto pides `/api/products`?
3. Repite con `/api/demo/offloaded?ms=5000`. ¿En qué hilo se ejecuta el trabajo?
4. (Opcional) Añade `.block()` a un `Mono` dentro de un controlador y observa la excepción.

> Pista para el punto 2: el número de hilos del *event loop* es igual al número de núcleos
> (`Runtime.getRuntime().availableProcessors()`, mínimo 4). Con suficientes peticiones bloqueantes el
> servidor deja de atender incluso peticiones no relacionadas.

## Para casa (opcional)

- Completar los ejercicios oficiales *Lite Rx API Hands-on*: <https://github.com/reactor/lite-rx-api-hands-on>
- Leer [Reactor — Which operator do I need?](https://projectreactor.io/docs/core/release/reference/apdx-operatorChoice.html)
- Revisar [Return Values](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/return-types.html)
  de los controladores WebFlux.

⬅️ Volver al [índice del día 1](README.md)
