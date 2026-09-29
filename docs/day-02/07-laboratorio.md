# 7. Laboratorio del día 2

> Proyecto: `examples/day-02` · Duración total aproximada: 70 min (repartidos entre los bloques)
>
> Todas las soluciones se han compilado y ejecutado contra el proyecto del día (Spring Boot 4.1.1) con un
> test de integración (`WebTestClient`) y uno unitario (`StepVerifier`).

## Parte 0 — Arranque (5 min)

```bash
cd examples/day-02
./mvnw test              # Windows: mvnw.cmd test   → 35 tests OK
./mvnw spring-boot:run
```

1. Abre `requests.http` y lanza las peticiones de la sección 1. Compara el cuerpo de un 404 del catálogo con
   el de un 404 de pedidos: ¿qué campos tienen en común?
2. Abre <http://localhost:8080/actuator/mappings> y localiza las rutas de `/api/orders`. ¿En qué
   `HandlerMapping` aparecen? ¿Y las de `/api/products`?

## Parte A — Controladores anotados (II) (15 min, tras el bloque 1)

### A1. Búsqueda por rango de precios

`GET /api/products/by-price?min=1000&max=2000` → productos con `min ≤ precio ≤ max`.
Ambos parámetros son obligatorios, `min ≥ 0`, `max > 0`, y **`min` no puede ser mayor que `max`**
(400 con `errors.validRange`).

<details><summary>Solución</summary>

```java
// catalog/PriceRange.java
public record PriceRange(@NotNull @PositiveOrZero BigDecimal min, @NotNull @Positive BigDecimal max) {

    @AssertTrue(message = "min no puede ser mayor que max")     // regla entre dos campos
    public boolean isValidRange() {
        return min == null || max == null || min.compareTo(max) <= 0;
    }

    public boolean contains(BigDecimal price) {
        return price.compareTo(min) >= 0 && price.compareTo(max) <= 0;
    }
}

// catalog/ProductController.java
@GetMapping("/by-price")
public Flux<Product> byPrice(@Valid @ModelAttribute PriceRange range) {
    return service.findAll(null).filter(p -> range.contains(p.price()));
}
```

```json
GET /api/products/by-price?min=100&max=50  → 400
{"detail":"La petición contiene 1 campo(s) no válido(s)","instance":"/api/products/by-price","status":400,
 "title":"Datos no válidos","type":"https://curso-webflux.example/problems/validation",
 "errors":{"validRange":"min no puede ser mayor que max"}}
```
</details>

### A2. Nombres de producto únicos (409 Conflict)

Al crear un producto, si ya existe otro con el mismo nombre (sin distinguir mayúsculas) responde **409** con un
`ProblemDetail` de tipo `product-duplicated`. Hazlo **sin** tocar `GlobalExceptionHandler`.

<details><summary>Solución</summary>

```java
// catalog/DuplicateProductException.java: al ser ErrorResponseException, ResponseEntityExceptionHandler
// (del que hereda GlobalExceptionHandler) la escribe como ProblemDetail sin código adicional
public class DuplicateProductException extends ErrorResponseException {
    public DuplicateProductException(String name) {
        super(HttpStatus.CONFLICT, Problems.of(HttpStatus.CONFLICT, "product-duplicated",
                "Producto duplicado", "Ya existe un producto llamado '" + name + "'"), null);
    }
}

// catalog/ProductService.java: validación de negocio reactiva (necesita consultar el repositorio)
public Mono<Product> create(ProductRequest request) {
    return repository.findAll()
            .any(p -> p.name().equalsIgnoreCase(request.name()))      // Mono<Boolean>
            .flatMap(exists -> exists
                    ? Mono.error(new DuplicateProductException(request.name()))
                    : repository.save(request.toProduct(null)));
}
```

```json
POST /api/products {"name":"auriculares", ...}  → 409 application/problem+json
{"detail":"Ya existe un producto llamado 'auriculares'","instance":"/api/products","status":409,
 "title":"Producto duplicado","type":"https://curso-webflux.example/problems/product-duplicated"}
```
</details>

## Parte B — Pedidos: validación reactiva (20 min, tras el bloque 2)

### B1. Importe máximo por pedido

Un pedido no puede superar **5 000 €**. Si lo supera: 422 con `ProblemDetail` que incluya `total` y `max`, y
**sin descontar stock**. Prueba: 5 portátiles (5 × 1099 = 5495 €).

<details><summary>Solución</summary>

```java
// orders/OrderLimitExceededException.java
public class OrderLimitExceededException extends ErrorResponseException {
    public OrderLimitExceededException(BigDecimal total, BigDecimal max) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, Problems.of(HttpStatus.UNPROCESSABLE_CONTENT, "order-limit-exceeded",
                "Importe máximo superado", "El total " + total + " supera el máximo permitido de " + max), null);
        getBody().setProperty("total", total);
        getBody().setProperty("max", max);
    }
}

// orders/OrderService.java: un paso más entre ⑤ (construir) y ⑥ (efectos)
static final BigDecimal MAX_TOTAL = new BigDecimal("5000");

public Mono<Order> create(OrderRequest request) {
    return Flux.fromIterable(request.details())
            // ... pasos ① a ④ sin cambios ...
            .map(details -> Order.create(request.customerId(), details))  // ⑤
            .flatMap(OrderService::rejectIfOverLimit)                     // ⑤b importe máximo
            .flatMap(this::reserveStockAndSave);                          // ⑥ no se ejecuta si ⑤b falla
}

private static Mono<Order> rejectIfOverLimit(Order order) {
    return order.total().compareTo(MAX_TOTAL) > 0
            ? Mono.error(new OrderLimitExceededException(order.total(), MAX_TOTAL))
            : Mono.just(order);
}
```

```json
{"detail":"El total 5495.00 supera el máximo permitido de 5000","instance":"/api/orders","status":422,
 "title":"Importe máximo superado","type":"https://curso-webflux.example/problems/order-limit-exceeded",
 "total":5495.00,"max":5000}
```

Versión imperativa equivalente: `if (order.total().compareTo(MAX_TOTAL) > 0) throw new OrderLimitExceededException(...);`.
Fíjate en que en la reactiva **no hace falta ningún `if` en `reserveStockAndSave`**: la señal de error salta
el paso ⑥.
</details>

### B2. Cancelar un pedido (devolviendo el stock)

`DELETE /api/orders/{id}` → 204; devuelve al catálogo las unidades de cada línea y borra el pedido. 404 si no
existe. Añade la ruta al `OrderRouter` (es un endpoint funcional).

<details><summary>Solución</summary>

```java
// orders/OrderRepository.java
public Mono<Boolean> deleteById(String id) {
    return Mono.fromSupplier(() -> store.remove(id) != null);
}

// orders/OrderService.java
public Mono<Void> cancel(String id) {
    return findById(id)                                                     // 404 si no existe
            .flatMap(order -> Flux.fromIterable(order.details())
                    .concatMap(detail -> products.update(detail.productId(),
                            product -> product.withStock(product.stock() + detail.quantity())))
                    .then(orders.deleteById(id)))
            .then();
}

// orders/OrderHandler.java
public Mono<ServerResponse> cancel(ServerRequest request) {
    return service.cancel(request.pathVariable("id"))
            .then(ServerResponse.noContent().build());
}

// orders/OrderRouter.java, dentro de .path("/api/orders", ...)
.DELETE("/{id}", handler::cancel)
```
</details>

### B3. Pregunta: ¿por qué `concatMap` en `reserveStockAndSave` y `cancel`?

<details><summary>Respuesta</summary>

Son **efectos secundarios** (modifican datos). `concatMap` los ejecuta uno detrás de otro y en orden: si uno
falla, los siguientes no se lanzan, y el orden de ejecución es predecible. Para **lecturas** independientes
(validar líneas) conviene la concurrencia de `flatMapSequential`; para **escrituras**, suele preferirse el orden.
</details>

### B4. Limitar la concurrencia

Un catálogo real no debería recibir 20 consultas simultáneas por cada pedido. Haz que `create` consulte **como
máximo 2 líneas a la vez** y ajusta `OrderServiceTest.createQueriesTheCatalogConcurrently`: ¿cuánto tarda ahora
un pedido de 5 líneas?

<details><summary>Solución</summary>

```java
.flatMapSequential(indexed -> checkDetail(indexed.getT1().intValue(), indexed.getT2()), 2)   // maxConcurrency
```

5 líneas de 2 en 2 = 3 "rondas" de 50 ms = **150 ms**:

```java
StepVerifier.withVirtualTime(() -> service.create(fiveDetails()))
        .expectSubscription()
        .expectNoEvent(Duration.ofMillis(149))
        .thenAwait(Duration.ofMillis(1))
        .expectNextCount(1)
        .expectComplete()
        .verify(Duration.ofSeconds(2));
```

> ⚠️ Con tiempo virtual, si el evento esperado no llega nunca (porque el tiempo virtual no avanza más),
> `verifyComplete()` se queda **esperando indefinidamente**. Por eso los tests del proyecto terminan con
> `verify(Duration.ofSeconds(2))`: un límite en tiempo **real** que convierte el cuelgue en un fallo.
</details>

## Parte C — Endpoints funcionales (15 min, tras el bloque 3)

### C1. Líneas de un pedido

`GET /api/orders/{id}/details` → solo las líneas del pedido (JSON array). 404 `ProblemDetail` si no existe.

<details><summary>Solución (y una trampa)</summary>

```java
public Mono<ServerResponse> findDetails(ServerRequest request) {
    return service.findById(request.pathVariable("id"))
            .flatMap(order -> ServerResponse.ok()
                    .body(Flux.fromIterable(order.details()), OrderDetail.class));
}
// Router: .GET("/{id}/details", handler::findDetails)
```

❌ Trampa: `ServerResponse.ok().body(service.findById(id).flatMapMany(o -> Flux.fromIterable(o.details())), OrderDetail.class)`.
Aquí el *handler* devuelve **enseguida** un `ServerResponse` 200 y el error 404 aparece después, **mientras se
escribe el cuerpo**: `onError` del router ya no lo ve y el cliente recibe un **500** (comprobado) en lugar del
404. Decide el estado **antes** de construir la respuesta (con `flatMap` sobre el `Mono` del pedido).
</details>

### C2. Versión 2.0 de un pedido (versionado en endpoints funcionales)

Con `API-Version: 2.0`, `GET /api/orders/{id}` devuelve un resumen `{id, customerId, lines, total}`; sin
cabecera (1.0), el pedido completo.

<details><summary>Solución</summary>

```java
// orders/OrderSummary.java
public record OrderSummary(String id, String customerId, int lines, BigDecimal total) {
    public static OrderSummary from(Order order) {
        return new OrderSummary(order.id(), order.customerId(), order.details().size(), order.total());
    }
}

// orders/OrderHandler.java
public Mono<ServerResponse> findSummary(ServerRequest request) {
    return service.findById(request.pathVariable("id"))
            .map(OrderSummary::from)
            .flatMap(summary -> ServerResponse.ok().bodyValue(summary));
}

// orders/OrderRouter.java: la ruta versionada ANTES que la genérica (gana la primera que coincide)
import static org.springframework.web.reactive.function.server.RequestPredicates.version;

.GET("/{id}", version("2.0"), handler::findSummary)
.GET("/{id}", accept(MediaType.APPLICATION_JSON), handler::findById)
```

```json
{"id":"304eb37d-...","customerId":"lab-c","lines":2,"total":119.85}
```
</details>

## Parte D — URIs y CORS (10 min, tras los bloques 4 y 5)

### D1. Un fallo escondido en la cabecera `Location`

Lanza `POST /api/orders?debug=true` con un pedido válido y mira la cabecera `Location`. ¿Qué ha pasado? Arréglalo.

<details><summary>Solución</summary>

```text
Location: http://localhost:8080/api/orders/98a2117e-...?debug=true      ← se arrastra la query de la petición
```

`request.uriBuilder()` parte de la URI **completa** de la petición, incluida la query. Hay que limpiarla:

```java
ServerResponse.created(request.uriBuilder().replaceQuery(null).path("/{id}").build(order.id()))
```

(El `UriComponentsBuilder` que recibe un controlador anotado no tiene este problema: solo trae esquema, host,
puerto y *context path*.)
</details>

### D2. La SPA también necesita los pedidos

La aplicación de `http://localhost:5173` recibe 403 al llamar a `/api/orders` (compruébalo con la petición CORS
de `requests.http` cambiando la URL). Permítele usar los pedidos **sin** añadir ese origen a todo `/api/**`.
¿Qué test existente deja de pasar y por qué?

<details><summary>Solución</summary>

En `WebConfig.addCorsMappings`, una configuración específica para los pedidos:

```java
registry.addMapping("/api/orders/**")
        .allowedOrigins("http://127.0.0.1:8080", "http://localhost:5173")
        .allowedMethods("GET", "POST", "PUT", "DELETE")
        .allowedHeaders("Content-Type", "API-Version")
        .exposedHeaders("Location", "X-Response-Time")
        .maxAge(1800);
registry.addMapping("/api/**")
        // ... la configuración que ya había
```

`/api/orders/**` también coincide con `/api/orders`. Deja de pasar
`CorsTest.crossOriginAnnotationAddsAnOriginToOneController`, que comprobaba justo el 403 anterior:
actualízalo para esperar `isOk()`.
</details>

## Parte E — Configuración y versionado (10 min, tras el bloque 6)

### E1. Anunciar que la versión 1.0 está obsoleta

La 1.0 se considera obsoleta desde el 1/10/2026 y se retirará el 1/1/2027. Haz que sus respuestas lleven las
cabeceras `Deprecation` y `Sunset`.

<details><summary>Solución</summary>

```java
@Override
public void configureApiVersioning(ApiVersionConfigurer configurer) {
    StandardApiVersionDeprecationHandler deprecation = new StandardApiVersionDeprecationHandler();
    deprecation.configureVersion("1.0")
            .setDeprecationDate(ZonedDateTime.parse("2026-10-01T00:00:00Z"))
            .setSunsetDate(ZonedDateTime.parse("2027-01-01T00:00:00Z"));

    configurer.useRequestHeader("API-Version")
            .addSupportedVersions("1.0", "2.0")
            .setDefaultVersion("1.0")
            .setDeprecationHandler(deprecation);
}
```

```http
GET /api/products/4          (sin cabecera → 1.0)
Deprecation: @1790812800     ← fecha como segundos Unix (RFC 9745)
Sunset: Fri, 1 Jan 2027 00:00:00 GMT
```

Como la 1.0 es la versión **por defecto**, las cabeceras aparecen en todas las peticiones que no indican
versión, incluidas las de `/api/orders`. Con `API-Version: 2.0` no aparecen.
</details>

### E2. Versión 2.1 sin duplicar código

Añade la versión `2.1` a las soportadas. `GET /api/products/4` con `API-Version: 2.1` debe devolver el mismo
formato que la 2.0 **sin** escribir otro método.

<details><summary>Solución</summary>

```java
configurer.addSupportedVersions("1.0", "2.0", "2.1");

@GetMapping(path = "/{id}", version = "2.0+")      // versión base: 2.0 y siguientes
public Mono<ProductV2> findByIdV2(@PathVariable String id) { ... }
```

Los endpoints sin `version` (`/count`, `/search`...) siguen respondiendo a cualquier versión.
</details>

## Para casa (opcional)

- Reescribe `OrderService.create` en estilo imperativo (como en el bloque 2.3) dentro de un proyecto Spring MVC
  y mide con [hey](https://github.com/rakyll/hey) cuántas peticiones por segundo aguanta cada versión con
  `-c 200`. Repite activando `spring.threads.virtual.enabled=true` (Java 21+).
- Lee [Reactor — Which operator do I need?](https://projectreactor.io/docs/core/release/reference/apdx-operatorChoice.html),
  sección *"I want to handle errors"*.

⬅️ Volver al [índice del día 2](README.md)
