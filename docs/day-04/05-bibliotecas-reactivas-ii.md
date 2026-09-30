# 5. Bibliotecas reactivas (II): interoperabilidad y `Context`

> Temario: **Bibliotecas reactivas** (II) · Duración: 20 min
> Referencia oficial: [Spring WebFlux — Reactive Libraries](https://docs.spring.io/spring-framework/reference/web/webflux-reactive-libraries.html) ·
> [Reactor — Context](https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html)
>
> Código: `interop/InteropController.java` · Tests: `interop/InteropTest`, `reactor/ContextTest`

## 5.1 WebFlux no obliga a usar Reactor

Reactor es la biblioteca que usa WebFlux por dentro, pero en los controladores (y en `WebClient`) se pueden usar
otros tipos asíncronos. La pieza que lo permite es el **`ReactiveAdapterRegistry`**: un registro de adaptadores
entre cada tipo conocido y un `Publisher` de Reactive Streams.

| Biblioteca | Un valor | Varios valores |
|---|---|---|
| Reactor | `Mono` | `Flux` |
| RxJava 3 | `Single`, `Maybe`, `Completable` | `Flowable` (con *backpressure*), `Observable` |
| JDK | `CompletableFuture` / `CompletionStage` | `Flow.Publisher` |
| Kotlin | funciones `suspend` | `Flow` |
| SmallRye Mutiny | `Uni` | `Multi` |

Para cada valor que devuelve un controlador, WebFlux pregunta al registro: *¿es reactivo? ¿uno o varios valores?
¿cómo lo convierto en `Publisher`?*

```java
// interop/InteropController.java
@GetMapping("/rx/low-stock")
public Flowable<Product> lowStock(@RequestParam(defaultValue = "10") int max) {
    return Flowable.fromPublisher(service.findAll(null))
            .filter(product -> product.stock() < max);          // operador de RxJava
}

@GetMapping("/rx/products/{id}")
public Single<ProductV2> product(@PathVariable String id) {
    return Single.fromPublisher(service.findById(id)).map(ProductV2::from);
}

@GetMapping("/future/count")
public CompletableFuture<Long> count() {
    return service.findAll(null).count().toFuture();
}
```

- `Flowable` se escribe igual que un `Flux`: array JSON, NDJSON o SSE según el `Accept`.
- Los errores de un `Single` pasan por el mismo `@RestControllerAdvice` (404 → `ProblemDetail`).
- La conversión Reactor ↔ RxJava es directa porque **ambos implementan Reactive Streams**:
  `Flowable.fromPublisher(flux)`, `Flux.from(flowable)`. Sin bloquear ni copiar a una lista.

> ⚠️ `CompletableFuture` es **impaciente** (*eager*): `toFuture()` se suscribe en el acto, no se puede cancelar ni
> "volver a suscribir" para reintentar, y pierde el `Context` de la petición. Úsalo solo en los bordes (una API que lo
> exige), no dentro de un pipeline.

📄 `InteropTest.reactiveAdapterRegistryConvertsBetweenLibraries`:

```java
ReactiveAdapter single = ReactiveAdapterRegistry.getSharedInstance().getAdapter(Single.class);
single.isMultiValue();                                   // false
single.toPublisher(Single.just("de RxJava"));            // RxJava -> Publisher (escribir la respuesta)
flowable.fromPublisher(Flux.just(1, 2, 3));              // Publisher -> RxJava (un @RequestBody Flowable<T>)
```

## 5.2 Kotlin: corrutinas

En Kotlin, WebFlux admite funciones `suspend` y `Flow` en lugar de `Mono`/`Flux`: el código parece imperativo pero
no bloquea (la corrutina se suspende y libera el hilo). Fragmento según la documentación oficial (el proyecto del
curso es Java y no lo incluye; hace falta `kotlinx-coroutines-reactor`):

```kotlin
@RestController
class ProductController(private val repository: ProductRepository) {

    @GetMapping("/api/products/{id}")
    suspend fun findById(@PathVariable id: String): Product =
        repository.findById(id) ?: throw ProductNotFoundException(id)       // repositorio CoroutineCrudRepository

    @GetMapping("/api/products")
    fun findAll(): Flow<Product> = repository.findAll()
}
```

| Reactor | Corrutinas |
|---|---|
| `Mono<T>` | `suspend fun ...: T?` |
| `Flux<T>` | `Flow<T>` |
| `flatMap`, `zip` | llamadas secuenciales, `async { }` / `awaitAll()` |
| `mono.awaitSingle()` / `flux.asFlow()` | puentes entre ambos mundos |

> ¿Y los **hilos virtuales** de Java 21 ([JEP 444](https://openjdk.org/jeps/444))? Dan la misma escalabilidad de E/S
> con código **bloqueante** (Spring MVC + `spring.threads.virtual.enabled=true`). No sustituyen a lo que aporta el
> modelo reactivo: *streaming* con *backpressure*, composición declarativa de llamadas concurrentes, cancelación
> y operadores con tiempo. Se habla de ello en el [cierre del curso](07-cierre-del-curso.md#73-cuándo-webflux-y-cuándo-no).

## 5.3 El `Context` de Reactor, recapitulado

Ya lo hemos usado tres veces: el `X-Request-Id` (día 3), el `SecurityContext` de Spring Security y, hoy, la
**transacción de R2DBC**. Tres reglas que se comprueban en 📄 `ContextTest`:

```java
// 1. Viaja del suscriptor HACIA ARRIBA: contextWrite solo lo ven los operadores ANTERIORES
greeting()                                                    // "petición abc"
    .contextWrite(Context.of(CorrelationId.CONTEXT_KEY, "abc"))
    .flatMap(text -> greeting()...)                           // "petición sin id": está DEBAJO

// 2. Se lee con deferContextual (no hay ThreadLocal)
static Mono<String> greeting() {
    return Mono.deferContextual(ctx -> Mono.just("petición " + CorrelationId.from(ctx).orElse("sin id")));
}

// 3. No depende del hilo: sobrevive a publishOn / subscribeOn
```

Por eso `CorrelationIdWebFilter` hace `chain.filter(exchange).contextWrite(...)`: al estar al final de la cadena, todo
lo que hay "encima" (controlador, servicio, `WebClient`, repositorio) lo ve.

### Del `Context` a un `ThreadLocal`: logs con el id de la petición (MDC)

Las bibliotecas que leen `ThreadLocal` (el MDC de los logs, OpenTelemetry...) no ven el `Context`. Desde Reactor 3.5 +
Micrometer Context Propagation se puede **restaurar** automáticamente el `ThreadLocal` en cada operador. Fragmento
verificado con una copia del proyecto (no está en el ejemplo, requiere la dependencia `io.micrometer:context-propagation`,
cuya versión gestiona Boot):

```properties
spring.reactor.context-propagation=auto        # Boot llama a Hooks.enableAutomaticContextPropagation()
```

```java
// Al arrancar: la clave "requestId" del Context se copia en el MDC (y se limpia) en cada operador
ContextRegistry.getInstance().registerThreadLocalAccessor("requestId",
        () -> MDC.get("requestId"), value -> MDC.put("requestId", value), () -> MDC.remove("requestId"));

Mono.just("x")
        .publishOn(Schedulers.parallel())
        .map(ignored -> MDC.get("requestId"))        // "req-7": un log.info() aquí llevaría el id
        .contextWrite(Context.of("requestId", "req-7"));
```

Sin el *hook* el MDC estaría vacío en ese `map` (otro hilo). Con el patrón de log `%X{requestId}` todas las líneas de
una petición llevarían su id. Es lo que hace Micrometer Tracing con el *trace id*.

## Referencias para ampliar

- Spring WebFlux — Reactive Libraries: <https://docs.spring.io/spring-framework/reference/web/webflux-reactive-libraries.html>
- Spring Framework — Kotlin Coroutines: <https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html>
- RxJava: <https://github.com/ReactiveX/RxJava> · SmallRye Mutiny: <https://smallrye.io/smallrye-mutiny/latest/>
- Reactive Streams: <https://www.reactive-streams.org/>
- Reactor — Adding a Context to a Reactive Sequence: <https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html>
- Micrometer Context Propagation: <https://docs.micrometer.io/context-propagation/reference/>
- JEP 444 — Virtual Threads: <https://openjdk.org/jeps/444>

➡️ Siguiente: [6. Laboratorio](06-laboratorio.md) · [7. Cierre del curso](07-cierre-del-curso.md)
