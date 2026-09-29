# 3. Puntos finales funcionales

> Temario: **Puntos finales funcionales** · Duración: 50 min
> Referencia oficial: [Spring WebFlux — Functional Endpoints](https://docs.spring.io/spring-framework/reference/web/webflux-functional.html)
>
> Código: `orders/OrderRouter.java`, `orders/OrderHandler.java` · Tests: `orders/OrderRoutesTest.java`

## 3.1 Otro modelo de programación sobre el mismo núcleo

WebFlux ofrece dos formas de escribir el servidor, y ambas corren sobre el mismo `DispatcherHandler` del día 1:

```text
                        ┌── RouterFunctionMapping (orden -1) ──► HandlerFunctionAdapter ──► ServerResponseResultHandler
DispatcherHandler ──────┤                                            (endpoints funcionales: pedidos)
                        └── RequestMappingHandlerMapping (orden 0) ─► RequestMappingHandlerAdapter ─► ResponseBodyResultHandler
                                                                     (controladores anotados: catálogo)
```

| | Controladores anotados | Endpoints funcionales |
|---|---|---|
| Rutas | Anotaciones sobre métodos (`@GetMapping`) | **Código**: un bean `RouterFunction` |
| Handler | Método con argumentos "mágicos" (`@PathVariable`, `@RequestBody`...) | `HandlerFunction`: `ServerRequest` → `Mono<ServerResponse>` |
| Quién decide cuándo y cómo | El framework (reflexión, *argument resolvers*) | Tu código, de forma explícita |
| Validación | `@Valid` automática | **Manual** con un `Validator` |
| Errores | `@ExceptionHandler` / `@ControllerAdvice` | `onError` / `filter` en el `RouterFunction` |
| Inspiración | Spring MVC | Programación funcional (rutas como datos, composición) |

Ninguno es "más reactivo": los dos devuelven `Mono`/`Flux` y comparten codecs, `WebFilter`s y configuración.
En el ejemplo conviven: el catálogo es anotado y los pedidos se exponen **de las dos formas** sobre el
mismo `OrderService`: `/api/orders` (funcional: `OrderRouter` + `OrderHandler`) y `/api/annotated/orders`
(anotado: `OrderController`). Compara las dos clases para ver cada fila de la tabla anterior.

## 3.2 `HandlerFunction`: `ServerRequest` → `Mono<ServerResponse>`

```java
@FunctionalInterface
public interface HandlerFunction<T extends ServerResponse> {
    Mono<T> handle(ServerRequest request);
}
```

Por organización se agrupan en una clase `*Handler` (aquí, un `@Component` con dependencias inyectadas):

```java
// orders/OrderHandler.java
public Mono<ServerResponse> findAll(ServerRequest request) {
    Flux<Order> result = request.queryParam("customerId")          // Optional<String>
            .map(service::findByCustomer)
            .orElseGet(service::findAll);
    return ServerResponse.ok().body(result, Order.class);           // Flux + clase del elemento
}

public Mono<ServerResponse> findById(ServerRequest request) {
    return service.findById(request.pathVariable("id"))             // 404 -> OrderNotFoundException
            .flatMap(order -> ServerResponse.ok().bodyValue(order));
}
```

| Anotado | `ServerRequest` |
|---|---|
| `@PathVariable String id` | `request.pathVariable("id")` |
| `@RequestParam String c` | `request.queryParam("c")` → `Optional<String>` |
| `@RequestHeader` | `request.headers().firstHeader("X")` |
| `@RequestBody Mono<T>` / `Flux<T>` | `request.bodyToMono(T.class)` / `bodyToFlux(T.class)` |
| formulario / multipart | `request.formData()` / `request.multipartData()` |
| `Mono<Principal>` | `request.principal()` |
| `UriComponentsBuilder` | `request.uriBuilder()` (bloque 4) |
| atributos del *exchange* | `request.attribute("x")` / `request.exchange()` |

| Anotado | `ServerResponse` |
|---|---|
| `Mono<T>` (200) | `ServerResponse.ok().bodyValue(obj)` |
| `Flux<T>` | `ServerResponse.ok().body(flux, T.class)` |
| `ResponseEntity.created(uri)` | `ServerResponse.created(uri).bodyValue(obj)` |
| `@ResponseStatus(NO_CONTENT)` | `ServerResponse.noContent().build()` |
| SSE | `ServerResponse.ok().contentType(TEXT_EVENT_STREAM).body(flux, T.class)` |

## 3.3 `RouterFunction`: rutas como datos

```java
// orders/OrderRouter.java
@Configuration
public class OrderRouter {

    @Bean
    public RouterFunction<ServerResponse> orderRoutes(OrderHandler handler) {
        return route()
                .path("/api/orders", orders -> orders                                  // rutas anidadas
                        .GET("/{id}", accept(MediaType.APPLICATION_JSON), handler::findById)
                        .GET(handler::findAll)                                         // GET /api/orders
                        .POST(contentType(MediaType.APPLICATION_JSON), handler::create))
                .filter((request, next) -> {                                           // 3.5
                    log.debug("[orders] {} {}", request.method(), request.path());
                    return next.handle(request);
                })
                .onError(ErrorResponse.class::isInstance,                               // 3.6
                        (error, request) -> problem((ErrorResponse) error, request))
                .build();
    }
}
```

- Spring Boot registra **cualquier bean `RouterFunction`**; puede haber varios (uno por módulo).
- Las rutas se evalúan **en orden** y gana la **primera** que coincide: las específicas, antes que las genéricas.
- `path(prefijo, builder -> ...)` y `nest(predicado, builder -> ...)` agrupan rutas que comparten condiciones.

### Predicados (`RequestPredicates`)

| Predicado | Condición |
|---|---|
| `GET("/x")`, `POST(...)`... | Método + patrón de ruta (misma sintaxis `PathPattern` que las anotaciones) |
| `accept(MediaType...)` | Cabecera `Accept` |
| `contentType(MediaType...)` | Cabecera `Content-Type` |
| `queryParam("name", valor -> ...)` | Parámetro de consulta |
| `headers(h -> ...)` | Cualquier condición sobre las cabeceras |
| `version("2.0")` | Versión de la API (Spring 7, bloque 6) |
| `a.and(b)`, `a.or(b)`, `a.negate()` | Composición |

### Un predicado que no se cumple = no hay ruta

En un controlador anotado, un `consumes` que no coincide produce **415**. En el modelo funcional el predicado
forma parte de la **selección** de la ruta: si no se cumple, esa ruta no existe para la petición, y si ninguna
otra coincide la respuesta es **404**:

```bash
curl -i -X POST http://localhost:8080/api/orders -H "Content-Type: text/plain" -d 'hola'   # -> 404
```

(Lo comprueba `OrderRoutesTest.unmatchedPredicateMeansNoRoute`.) Si se quiere un 415, hay que añadir una
ruta "de recogida" para ese caso o validar la cabecera dentro del *handler*.

## 3.4 Filtros de *handler*

| Método del *builder* | Cuándo se ejecuta | Uso típico |
|---|---|---|
| `before(request -> request)` | Antes del *handler*; puede modificar la petición | Añadir atributos, cabeceras |
| `after((request, response) -> response)` | Después; puede modificar la respuesta | Añadir cabeceras de respuesta |
| `filter((request, next) -> ...)` | Envuelve al *handler*: decide si llamarlo (`next.handle(request)`) | Log, autorización, medir tiempos |
| `onError(predicado, (error, request) -> ...)` | Si el *handler* emite un error que cumple el predicado | Traducir excepciones a respuestas |

Los filtros de un *builder* se aplican **solo a las rutas de ese *builder*** (si están dentro de `path(...)`,
solo a las anidadas). Un `WebFilter` (día 1), en cambio, se aplica a **toda** la aplicación y a ambos modelos.

## 3.5 Gestión de errores

`@RestControllerAdvice` **no** se aplica a los endpoints funcionales. En el ejemplo, `onError` convierte en
`ProblemDetail` cualquier excepción que sea un `ErrorResponse`: las nuestras (`OrderRejectedException`,
`OrderNotFoundException`, `InvalidOrderException`) y las de Spring (`ServerWebInputException` por JSON mal
formado, etc.):

```java
private static Mono<ServerResponse> problem(ErrorResponse error, ServerRequest request) {
    ProblemDetail body = error.getBody();
    if (body.getInstance() == null) {
        body.setInstance(URI.create(request.path()));
    }
    return ServerResponse.status(error.getStatusCode())
            .headers(headers -> headers.addAll(error.getHeaders()))
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyValue(body);
}
```

> ℹ️ Existe el atajo `ServerResponse.from(errorResponse)`, pero en Spring Framework 7.0.9 responde con
> `Content-Type: application/json` en lugar de `application/problem+json` y sin `instance`. Por eso el ejemplo
> construye la respuesta a mano, igual que hace `ResponseEntityExceptionHandler` en los controladores anotados.

Las excepciones que no cumplen el predicado siguen su camino hasta los `WebExceptionHandler` y Spring Boot
responde con su JSON de error por defecto (500).

## 3.6 Validación manual

Sin `@Valid`, el *handler* invoca el `Validator` (el mismo `LocalValidatorFactoryBean` de Bean Validation que
configura Spring Boot) y convierte el resultado en una **señal**:

```java
public Mono<ServerResponse> create(ServerRequest request) {
    return request.bodyToMono(OrderRequest.class)
            .switchIfEmpty(Mono.error(() -> new ServerWebInputException("El cuerpo de la petición es obligatorio")))
            .flatMap(this::validate)                       // 400 estructural
            .flatMap(service::create)                      // 422 de negocio (bloque 2)
            .flatMap(order -> ServerResponse
                    .created(request.uriBuilder().path("/{id}").build(order.id()))
                    .bodyValue(order));
}

private Mono<OrderRequest> validate(OrderRequest body) {
    Errors errors = new DirectFieldBindingResult(body, "order");
    validator.validate(body, errors);
    return errors.hasErrors() ? Mono.error(new InvalidOrderException(errors)) : Mono.just(body);
}
```

> ⚠️ **Detalle encontrado al preparar el ejemplo:** el atajo `validator.validateObject(body)` usa un `SimpleErrors`
> que no sabe leer rutas anidadas de un record (`details[0].quantity`) y acaba en un **500**. Con
> `DirectFieldBindingResult` (acceso directo a campos) funciona. La documentación oficial usa
> `BeanPropertyBindingResult`, que sirve para JavaBeans con *getters* `getXxx()`.

## 3.7 Probar endpoints funcionales

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class OrderRoutesTest {

    @Autowired WebTestClient client;

    @Test
    void businessErrorsAre422ProblemDetailWithEveryLine() {
        client.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.errors.length()").isEqualTo(2);
    }
}
```

Sin arrancar el servidor, también se puede probar un `RouterFunction` aislado con
`WebTestClient.bindToRouterFunction(routes).build()` (día 4).

## 3.8 ¿Cuál elegir?

| Elige anotados si... | Elige funcionales si... |
|---|---|
| El equipo viene de Spring MVC | Prefieres ver las rutas como código y componerlas |
| Quieres validación y errores "automáticos" (`@Valid`, `@ControllerAdvice`) | Quieres control explícito de cada paso |
| Documentas con OpenAPI a partir de las anotaciones | Construyes rutas dinámicamente o con condiciones complejas |
| | Buscas un arranque más ligero (sin reflexión sobre métodos) |

Pueden convivir en la misma aplicación, como en el ejemplo.

## Referencias para ampliar

- Functional Endpoints: <https://docs.spring.io/spring-framework/reference/web/webflux-functional.html>
- `RouterFunctions` (Javadoc): <https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/reactive/function/server/RouterFunctions.html>
- `RequestPredicates` (Javadoc): <https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/reactive/function/server/RequestPredicates.html>
- `ServerResponse` (Javadoc): <https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/reactive/function/server/ServerResponse.html>
- Spring Boot — Reactive Web Applications (endpoints funcionales): <https://docs.spring.io/spring-boot/reference/web/reactive.html>

➡️ Siguiente: [4. URI's](04-uris.md)
