# 5. Controladores anotados (I)

> Temario: **Controladores anotados** (primera parte; se completa el día 2) · Duración: 30 min de teoría + 30 min de laboratorio
> Referencia oficial: [Spring WebFlux — Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/webflux/controller.html)
>
> Código: `catalog/ProductController.java`, `catalog/ProductService.java`, `catalog/ProductRepository.java`

## 5.1 Mismo modelo que Spring MVC

WebFlux usa **las mismas anotaciones** que Spring MVC (`spring-web`): `@Controller`, `@RestController`,
`@RequestMapping`, `@GetMapping`, `@PathVariable`, `@RequestParam`, `@RequestBody`, `@ResponseStatus`… Lo que
cambia es que los métodos devuelven **tipos reactivos** y pueden recibir el cuerpo de forma reactiva.

```java
@RestController                       // = @Controller + @ResponseBody en cada método
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) { this.service = service; }

    @GetMapping
    public Flux<Product> findAll(@RequestParam(required = false) String category) {
        return service.findAll(category);
    }

    @GetMapping("/{id}")
    public Mono<Product> findById(@PathVariable String id) {
        return service.findById(id);  // Mono vacío → error 404 en el servicio (switchIfEmpty)
    }
}
```

Los controladores se detectan por *component scanning* (`@SpringBootApplication` ya lo activa) y los registra
el `RequestMappingHandlerMapping`.

## 5.2 Mapeo de peticiones (resumen)

| Anotación / atributo | Ejemplo |
|---|---|
| `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping` | `@PostMapping("/api/products")` |
| Variables de ruta | `@GetMapping("/{id}")` + `@PathVariable String id` |
| Patrones | `/resources/*.png`, `/files/{*path}` (captura el resto de la ruta) |
| `consumes` / `produces` | `@GetMapping(path = "/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)` |
| `params` / `headers` | `@GetMapping(path = "/x", params = "version=2")` |

Los patrones se procesan con `PathPattern` (más eficiente que `AntPathMatcher`). Detalle completo el día 2.

## 5.3 Tipos de retorno reactivos

| Retorno | Resultado HTTP |
|---|---|
| `Mono<T>` | Un objeto serializado (p. ej., JSON). Si el `Mono` está **vacío**, respuesta 200 sin cuerpo |
| `Flux<T>` | Array JSON (`application/json`) o *streaming* (`application/x-ndjson`, `text/event-stream`) |
| `Mono<ResponseEntity<T>>` | Control total de estado, cabeceras y cuerpo, decidido de forma asíncrona |
| `ResponseEntity<Mono<T>>` / `ResponseEntity<Flux<T>>` | Estado y cabeceras conocidos al momento; cuerpo asíncrono |
| `Mono<Void>` | Sin cuerpo; la respuesta se envía cuando el `Mono` completa (combinar con `@ResponseStatus`) |
| `Flux<ServerSentEvent<T>>` | SSE con control de `id`, `event`, `retry`, comentarios |
| Otros adaptables | `CompletableFuture<T>`, tipos RxJava… (vía `ReactiveAdapterRegistry`) |
| Valor plano `T` | Permitido, pero el método debe ser **no bloqueante** |

> ⚠️ **Nunca** llames a `block()` dentro de un controlador: se ejecuta en un hilo del *event loop*, y Reactor
> lanza `IllegalStateException: block()/blockFirst()/blockLast() are blocking, which is not supported in thread reactor-http-nio-*`.

Ejemplos del proyecto:

```java
// 201 Created + cabecera Location, decidido cuando termina el guardado
@PostMapping
public Mono<ResponseEntity<Product>> create(@Valid @RequestBody Mono<ProductRequest> request) {
    return request
            .flatMap(service::create)
            .map(p -> ResponseEntity.created(URI.create("/api/products/" + p.id())).body(p));
}

// 204 No Content; si no existe, el servicio emite ProductNotFoundException → 404
@DeleteMapping("/{id}")
@ResponseStatus(HttpStatus.NO_CONTENT)
public Mono<Void> delete(@PathVariable String id) {
    return service.delete(id);
}
```

## 5.4 Argumentos: `@RequestBody` reactivo

Diferencia notable respecto a MVC: el cuerpo puede declararse como `Mono<T>` o `Flux<T>`.

| Declaración | Comportamiento |
|---|---|
| `@RequestBody ProductRequest req` | WebFlux decodifica el cuerpo (sin bloquear) **antes** de invocar el método |
| `@RequestBody Mono<ProductRequest> req` | El método se invoca en cuanto llega la petición; el cuerpo se procesa al componer el `Mono` |
| `@RequestBody Flux<ProductRequest> req` | Procesar en *streaming* un array JSON o NDJSON grande, elemento a elemento |

Ambas formas son no bloqueantes. Con `@Valid`:

- Con `ProductRequest` plano, un error de validación lanza `WebExchangeBindException` → 400.
- Con `Mono<ProductRequest>`, el error llega como **señal `onError`** del `Mono` (se puede tratar con
  `onErrorResume`); si no se trata, también termina en 400.

Otros argumentos habituales: `@PathVariable`, `@RequestParam`, `@RequestHeader`, `@CookieValue`,
`ServerWebExchange`, `ServerHttpRequest`, `ServerHttpResponse`, `WebSession`, `Principal` (como `Mono`),
`@RequestPart`, `@ModelAttribute`. Lista completa: [Method Arguments](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/arguments.html).

> ❌ No se pueden usar `HttpServletRequest`/`HttpServletResponse`: WebFlux no se basa en la API Servlet.

## 5.5 *Streaming*: NDJSON y Server-Sent Events

El mismo `Flux` puede servirse de formas distintas según la cabecera `Accept` (negociación de contenido):

```bash
# Array JSON: el servidor agrega el Flux y responde de una vez
curl http://localhost:8080/api/products

# NDJSON: un objeto por línea, enviado según se produce (ideal entre servicios)
curl -H "Accept: application/x-ndjson" http://localhost:8080/api/products
```

Para flujos **infinitos** hacia un navegador se usa **SSE** (`text/event-stream`):

```java
@GetMapping(path = "/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ProductService.PriceChange> prices() {
    return service.priceTicker();   // Flux.interval(1s) → cambio de precio aleatorio
}
```

```bash
curl -N http://localhost:8080/api/products/prices
# data:{"productId":"3","name":"Monitor 27 pulgadas","oldPrice":249.00,"newPrice":255.95}
```

En el navegador (`src/main/resources/static/index.html`):

```javascript
const source = new EventSource('/api/products/prices');
source.onmessage = (e) => console.log(JSON.parse(e.data));
```

Cuando el cliente se desconecta (o llama a `source.close()`), Netty **cancela la suscripción** y el
`Flux.interval` deja de generar eventos: la cancelación de Reactive Streams llega hasta el origen.

| | SSE | NDJSON | WebSocket (día 4) |
|---|---|---|---|
| Dirección | Servidor → cliente | Servidor → cliente (o cliente → servidor) | Bidireccional |
| Protocolo | HTTP | HTTP | Upgrade a WS |
| Cliente navegador | `EventSource` (reconexión automática) | `fetch` + lectura del *stream* | `WebSocket` |
| Caso típico | Notificaciones, cotizaciones, progreso | *Streaming* entre microservicios | Chat, juegos, colaboración |

## 5.6 Arquitectura en capas reactiva

```text
ProductController  ──►  ProductService  ──►  ProductRepository
 (Mono/Flux)             (operadores)          (Mono/Flux: en memoria hoy, R2DBC/WebClient mañana)
```

Reglas:

1. **Todas las capas devuelven `Mono`/`Flux`** de extremo a extremo. Un solo punto bloqueante rompe el modelo.
2. **Nadie se suscribe** en el código de la aplicación: el framework lo hace.
3. Los casos "no encontrado" se expresan con `Mono.empty()` + `switchIfEmpty(Mono.error(...))`.
4. La latencia se simula con `delayElement`/`delayElements` (no bloqueantes), nunca con `Thread.sleep`.

## 5.7 Probar un controlador con `WebTestClient` (avance del día 4)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient      // Boot 4: org.springframework.boot.webtestclient.autoconfigure
class ProductControllerTest {

    @Autowired WebTestClient client;

    @Test
    void returns404WhenProductDoesNotExist() {
        client.get().uri("/api/products/{id}", "no-existe")
              .exchange()
              .expectStatus().isNotFound();
    }
}
```

📄 `src/test/.../catalog/ProductControllerTest.java`: lista, filtro, NDJSON, 404, ciclo crear-leer-borrar,
validación y SSE (con `StepVerifier` sobre el cuerpo de la respuesta).

> ℹ️ En Spring Boot 4 los *starters* de test están modularizados: `spring-boot-starter-webflux-test`
> aporta `WebTestClient` y `@WebFluxTest`.

## 5.8 Qué veremos el día 2 (Controladores anotados II)

Conversión de tipos y *data binding*, validación en profundidad, `@ExceptionHandler` y `@ControllerAdvice`,
respuestas de error estándar con `ProblemDetail` (RFC 9457), multipart, personalización de Jackson y
*API versioning*.

## Referencias para ampliar

- Annotated Controllers: <https://docs.spring.io/spring-framework/reference/web/webflux/controller.html>
- Mapping Requests: <https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-requestmapping.html>
- Method Arguments: <https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/arguments.html>
- Return Values: <https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/return-types.html>
- `@RequestBody`: <https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/requestbody.html>
- `ResponseEntity`: <https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/responseentity.html>
- Validation: <https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-validation.html>
- MDN — Using server-sent events (en español): <https://developer.mozilla.org/es/docs/Web/API/Server-sent_events/Using_server-sent_events>
- MDN — `EventSource`: <https://developer.mozilla.org/en-US/docs/Web/API/EventSource>
- WebTestClient: <https://docs.spring.io/spring-framework/reference/testing/webtestclient.html>
- Spring Boot — Testing Spring Boot Applications: <https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html>

➡️ Siguiente: [6. Laboratorio](06-laboratorio.md)
