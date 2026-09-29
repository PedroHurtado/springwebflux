# 1. Controladores anotados (II)

> Temario: **Controladores anotados** (segunda parte; la primera se vio el día 1) · Duración: 60 min
> Referencia oficial: [Spring WebFlux — Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/webflux/controller.html)
>
> Código: `catalog/ProductController.java`, `catalog/ProductSearch.java`, `error/GlobalExceptionHandler.java`,
> `config/StringToProductSortConverter.java`, `catalog/ProductImageStore.java`, `catalog/ProductV2.java`

## 1.1 Mapeo avanzado de peticiones

WebFlux compara las rutas con `PathPattern` (el mismo analizador que usa Spring MVC por defecto desde la 6.0):

| Patrón | Coincide con | Notas |
|---|---|---|
| `/api/products/{id}` | `/api/products/7` | variable de ruta → `@PathVariable String id` |
| `/api/products/{id:\d+}` | `/api/products/7`, **no** `/api/products/abc` | variable con expresión regular |
| `/files/{*path}` | `/files/a/b/c.txt` → `path = "/a/b/c.txt"` | captura el resto de la ruta (solo al final) |
| `/files/**` | `/files/a/b/c.txt` | cualquier número de segmentos |
| `/img/*.png` | `/img/logo.png` | un segmento |

Si varias rutas coinciden gana **la más específica**: `/api/products/search` antes que `/api/products/{id}`.
Por eso en el ejemplo conviven `/search`, `/top`, `/count` y `/prices` con `/{id}` sin conflicto.

Condiciones adicionales de `@RequestMapping` (y de `@GetMapping`, `@PostMapping`...):

```java
@PostMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)  // Content-Type
@GetMapping(path = "/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)          // Accept
@GetMapping(path = "/legacy", params = "format=csv", headers = "X-Client=admin")     // parámetros / cabeceras
@GetMapping(path = "/{id}", version = "2.0")                                         // API versioning (Spring 7, bloque 6)
```

| Si no se cumple... | Respuesta |
|---|---|
| `consumes` | 415 Unsupported Media Type |
| `produces` | 406 Not Acceptable |
| el método HTTP | 405 Method Not Allowed |
| `version` (versión no soportada) | 400 Bad Request |

`HEAD` y `OPTIONS` se atienden automáticamente para cualquier ruta `GET`.

## 1.2 Argumentos y valores de retorno (ampliación)

El día 1 vimos `@PathVariable`, `@RequestParam`, `@RequestBody` (también como `Mono`/`Flux`) y los retornos
`Mono`, `Flux`, `ResponseEntity`, `Flux<ServerSentEvent>`. Otros argumentos útiles:

| Argumento | Para qué |
|---|---|
| `@RequestHeader("API-Version") String v` | Una cabecera (o todas con `HttpHeaders`) |
| `@CookieValue("SESSION") String s` | Una cookie |
| `@ModelAttribute ProductSearch search` | Enlazar varios parámetros a un objeto (1.4) |
| `@RequestPart("file") Mono<FilePart> file` | Una parte de un `multipart/form-data` (1.8) |
| `UriComponentsBuilder uriBuilder` | Construir URIs relativas a la petición actual (bloque 4) |
| `ServerWebExchange exchange` | Acceso a todo: petición, respuesta, atributos, sesión |
| `Mono<Principal>` / `Mono<WebSession>` | Usuario autenticado (día 3) / sesión |

Lista completa: [Method Arguments](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/arguments.html)
y [Return Values](https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/return-types.html).

## 1.3 Conversión de tipos

Los valores que llegan en la URL, la query o las cabeceras son **texto**. Cuando el argumento es de otro tipo
(`int`, `BigDecimal`, `LocalDate`, un `enum`...), WebFlux lo convierte con el `ConversionService`:

```java
@GetMapping("/top")
public Flux<Product> top(@RequestParam(defaultValue = "3") int limit)   // "3" -> 3
```

Para tipos propios se registra un `Converter` (o un `Formatter`, si también hay que formatear a texto) en
`WebFluxConfigurer.addFormatters`:

```java
// config/StringToProductSortConverter.java
public class StringToProductSortConverter implements Converter<String, ProductSort> {
    @Override
    public ProductSort convert(String source) {
        return ProductSort.fromCode(source.trim());   // "price-desc" -> PRICE_DESC
    }
}

// config/WebConfig.java
@Override
public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(new StringToProductSortConverter());
}
```

Sin el conversor, Spring solo aceptaría el nombre exacto del `enum` (`?sort=PRICE_DESC`). Si la conversión
falla, la respuesta es **400**.

> 💡 Fechas: `@DateTimeFormat(iso = ISO.DATE) LocalDate from` en el argumento, o de forma global con las
> propiedades de Boot `spring.webflux.format.date`, `spring.webflux.format.date-time` y `spring.webflux.format.time`.

## 1.4 *Data binding* con `@ModelAttribute`

Cuando un endpoint recibe muchos parámetros de consulta, conviene agruparlos en un objeto. Con un **record**,
WebFlux usa *constructor binding*: llama al constructor con los parámetros cuyo nombre coincide con cada
componente, convirtiendo cada uno a su tipo.

```java
// catalog/ProductSearch.java
public record ProductSearch(String category, @Positive BigDecimal maxPrice, ProductSort sort) {
    public ProductSort sortOrDefault() { return sort == null ? ProductSort.NAME : sort; }
}

// catalog/ProductController.java
@GetMapping("/search")
public Flux<Product> search(@Valid @ModelAttribute ProductSearch search) {
    return service.search(search);
}
```

```bash
curl "http://localhost:8080/api/products/search?category=perifericos&maxPrice=100&sort=price-desc"
```

- Los parámetros que no llegan quedan a `null` (o al valor por defecto de un tipo primitivo).
- Si un parámetro de la URL no se llama igual que el componente, se usa `@BindParam("max-price")` en el componente.
- `@InitBinder` en un controlador o en un `@ControllerAdvice` personaliza el `WebDataBinder` (editores propios,
  campos permitidos...).

> ⚠️ **Seguridad (*mass assignment*):** enlazar peticiones directamente a entidades del dominio permite al
> cliente rellenar campos que no debería (p. ej. `stock` o `id`). Usa objetos específicos para la entrada
> (`ProductSearch`, `ProductRequest`) que solo contengan lo que el cliente puede enviar.

## 1.5 Validación

Spring Boot incluye Bean Validation (Hibernate Validator) con `spring-boot-starter-validation`. En WebFlux hay
tres puntos de validación:

| Qué se valida | Cómo | Excepción si falla | Estado |
|---|---|---|---|
| `@RequestBody` | `@Valid @RequestBody ProductRequest req` | `WebExchangeBindException` | 400 |
| `@ModelAttribute` | `@Valid @ModelAttribute ProductSearch search` | `WebExchangeBindException` | 400 |
| Parámetros sueltos (`@RequestParam`, `@PathVariable`, `@RequestHeader`) | Restricciones directamente en el parámetro: `@RequestParam @Min(1) @Max(10) int limit` | `HandlerMethodValidationException` | 400 |

La tercera fila es la **validación de métodos integrada** (Spring Framework 6.1+): basta con poner
restricciones de Bean Validation en los parámetros; no hace falta `@Validated` en la clase.

```java
@GetMapping("/top")
public Flux<Product> top(@RequestParam(defaultValue = "3") @Min(1) @Max(10) int limit) { ... }
```

Recordatorio del día 1: con `@Valid @RequestBody Mono<ProductRequest>`, el error **no se lanza** antes de invocar
el método, sino que llega como señal `onError` del `Mono` (se puede tratar con `onErrorResume` o dejar que
termine en 400).

Reglas que afectan a **varios campos** a la vez: un método `boolean isXxx()` anotado con `@AssertTrue`
(ver `orders/OrderRequest.isWithoutRepeatedProducts()`), o una restricción de clase propia.

> ℹ️ **Validación estructural vs. de negocio.** Bean Validation comprueba el **formato** de los datos sin hacer
> E/S (campo vacío, número negativo). Las reglas que necesitan consultar algo (¿existe el producto?, ¿hay stock?)
> no deben ir en anotaciones: son lógica de negocio reactiva. Es el tema del [bloque 2](02-pedidos-validacion-reactiva.md).

## 1.6 Gestión de excepciones: `@ExceptionHandler` y `@ControllerAdvice`

Un método `@ExceptionHandler` convierte una excepción en una respuesta. Puede estar:

- **en el propio controlador**: solo gestiona las excepciones de ese controlador;
- **en una clase `@ControllerAdvice` / `@RestControllerAdvice`**: gestiona las de **todos** los controladores
  (se puede acotar con `basePackages`, `assignableTypes` o `annotations`).

```java
@RestControllerAdvice                    // = @ControllerAdvice + @ResponseBody
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler                     // el tipo se deduce del parámetro
    public ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
        ProblemDetail problem = Problems.of(HttpStatus.NOT_FOUND, "product-not-found",
                "Producto no encontrado", ex.getMessage());
        problem.setProperty("productId", ex.getProductId());
        return problem;
    }
}
```

- El método puede devolver `ProblemDetail`, `ResponseEntity<...>`, un objeto (con `@ResponseStatus`) o un
  `Mono` de cualquiera de ellos.
- Una excepción emitida como **señal de error** (`Mono.error(...)`) se gestiona igual que una lanzada con `throw`.
- Los *advice* se aplican **solo a controladores anotados**. Los endpoints funcionales tienen su propio
  mecanismo (`onError`, bloque 3). Los errores que nadie gestiona llegan a los `WebExceptionHandler` (día 1) y
  Spring Boot los responde con su JSON de error por defecto.

## 1.7 *Error Responses*: `ProblemDetail` (RFC 9457)

La [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html) (que sustituye a la RFC 7807) define un formato
estándar para los errores de una API HTTP, con *content type* `application/problem+json`:

| Campo | Significado | Ejemplo |
|---|---|---|
| `type` | URI que identifica la **clase** de problema (documentable) | `https://curso-webflux.example/problems/product-not-found` |
| `title` | Resumen legible de esa clase de problema | `Producto no encontrado` |
| `status` | Código HTTP | `404` |
| `detail` | Explicación de **este** caso concreto | `Producto no encontrado: xx` |
| `instance` | URI de la petición que falló | `/api/products/xx` |
| *(extensiones)* | Cualquier dato adicional | `productId`, `errors` |

Respuesta real del ejemplo (`curl -i http://localhost:8080/api/products/xx`):

```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{"detail":"Producto no encontrado: xx","instance":"/api/products/xx","status":404,
 "title":"Producto no encontrado","type":"https://curso-webflux.example/problems/product-not-found",
 "productId":"xx"}
```

### Piezas de Spring

| Pieza | Qué es |
|---|---|
| `ProblemDetail` | El cuerpo del error (los campos de la tabla + `properties` para extensiones) |
| `ErrorResponse` | Interfaz: "sé responder como error" (estado, cabeceras y `ProblemDetail`) |
| `ErrorResponseException` | Excepción base que implementa `ErrorResponse`. Todas las excepciones web de Spring (`ResponseStatusException`, `WebExchangeBindException`, `ServerWebInputException`...) la extienden |
| `ResponseEntityExceptionHandler` | Clase base para un `@ControllerAdvice` que responde **todas** las excepciones de Spring WebFlux como `ProblemDetail` |

### Tres formas de producir un `ProblemDetail`

```java
// 1) @ExceptionHandler que devuelve ProblemDetail (excepción de negocio "pura", sin dependencias de HTTP)
@ExceptionHandler
public ProblemDetail handleProductNotFound(ProductNotFoundException ex) { ... }

// 2) La excepción ES un ErrorResponse: lleva su ProblemDetail dentro (orders/OrderRejectedException)
public class OrderRejectedException extends ErrorResponseException {
    public OrderRejectedException(List<DetailError> errors) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, Problems.of(HttpStatus.UNPROCESSABLE_CONTENT,
                "order-rejected", "Pedido rechazado", "El pedido tiene " + errors.size() + " línea(s) no válida(s)"), null);
        getBody().setProperty("errors", errors);
    }
}

// 3) Personalizar las respuestas de las excepciones de Spring sobrescribiendo ResponseEntityExceptionHandler
@Override
protected Mono<ResponseEntity<Object>> handleWebExchangeBindException(
        WebExchangeBindException ex, HttpHeaders headers, HttpStatusCode status, ServerWebExchange exchange) {
    ProblemDetail problem = ex.getBody();
    problem.setTitle("Datos no válidos");
    problem.setProperty("errors", /* campo -> mensaje */);
    return handleExceptionInternal(ex, problem, headers, status, exchange);
}
```

Resultado de la tercera (`POST /api/products` con nombre vacío y precio negativo):

```json
{"detail":"La petición contiene 2 campo(s) no válido(s)","instance":"/api/products","status":400,
 "title":"Datos no válidos","type":"https://curso-webflux.example/problems/validation",
 "errors":{"name":"no debe estar vacío","price":"debe ser mayor que 0"}}
```

> 💡 Si no necesitas personalizar nada, Spring Boot activa un `ResponseEntityExceptionHandler` por defecto con
> `spring.webflux.problemdetails.enabled=true`. En el ejemplo no hace falta porque declaramos el nuestro.
>
> 💡 Internacionalización: `title` y `detail` se pueden resolver desde `messages.properties` con las claves
> `problemDetail.title.<clase de la excepción>` y `problemDetail.<clase de la excepción>`.

## 1.8 Multipart (subida de ficheros)

```java
@PostMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@ResponseStatus(HttpStatus.CREATED)
public Mono<ImageInfo> uploadImage(@PathVariable String id, @RequestPart("file") Mono<FilePart> file) {
    return service.findById(id)                                   // 404 si el producto no existe
            .then(file)
            .filter(ProductController::isImage)
            .switchIfEmpty(Mono.error(() -> new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Solo se admiten imágenes (image/*)")))
            .flatMap(part -> images.store(id, part))
            .map(image -> image.info(id));
}
```

```bash
curl -F "file=@src/test/resources/teclado.png;type=image/png" http://localhost:8080/api/products/1/image
curl -o descarga.png http://localhost:8080/api/products/1/image
```

| Tipo | Uso |
|---|---|
| `Part` | Una parte cualquiera (`headers()`, `content()` como `Flux<DataBuffer>`) |
| `FilePart` | Parte con fichero: `filename()`, `transferTo(Path)` (escritura a disco no bloqueante) |
| `FormFieldPart` | Campo de texto del formulario: `value()` |
| `@RequestBody Flux<PartEvent>` | *Streaming* puro de las partes, sin acumular nada (ficheros enormes) |

El contenido llega **por trozos** (`Flux<DataBuffer>`). En `ProductImageStore` se juntan con
`DataBufferUtils.join(content, maxBytes)`, que falla con `DataBufferLimitException` si se supera el límite
(→ 413 en `GlobalExceptionHandler`). Límites globales con propiedades de Boot:
`spring.webflux.multipart.max-in-memory-size`, `max-disk-usage-per-part`, `max-parts`...

## 1.9 Jackson

WebFlux (de)serializa JSON con los *codecs* de Jackson. En Spring Framework 7 / Spring Boot 4 se usa
**Jackson 3**: el núcleo está en `tools.jackson.*`, pero las **anotaciones** siguen en
`com.fasterxml.jackson.annotation`.

Tres niveles de personalización, de más local a más global:

```java
// 1) Anotaciones en el tipo (catalog/ProductV2.java)
@JsonPropertyOrder({ "id", "name", "price", "priceWithVat", "available" })
@JsonInclude(JsonInclude.Include.NON_NULL)            // los null no se escriben
public record ProductV2(..., @JsonProperty("warning") String lowStockWarning) { }
```

```properties
# 2) Propiedades de Spring Boot (application.properties)
spring.jackson.deserialization.fail-on-unknown-properties=true   # {"color":"rojo"} -> 400
```

```java
// 3) Un bean JsonMapperBuilderCustomizer (org.springframework.boot.jackson.autoconfigure):
//    lo mismo que la propiedad anterior, pero en código (útil para lo que no tiene propiedad)
@Bean
JsonMapperBuilderCustomizer jsonCustomizer() {
    return builder -> builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);   // tools.jackson.databind
}
```

- Jackson 3 escribe las fechas `java.time` como texto ISO-8601 por defecto (`"createdAt":"2026-09-29T06:05:57.43Z"`).
- Serializadores propios: clases anotadas con `@JacksonComponent` (en Boot 3 era `@JsonComponent`).
- `@JsonView` también funciona en controladores WebFlux para devolver distintas "vistas" del mismo objeto.

> ⚠️ `FAIL_ON_UNKNOWN_PROPERTIES` está **desactivado** por defecto (en Jackson 3 ya es el valor por defecto de la
> librería; con Jackson 2 lo desactivaba Spring Boot): un campo mal escrito (`"quantiy"`) se ignora en silencio.
> En el ejemplo se activa para que el cliente reciba un 400.

## Referencias para ampliar

- Request Mapping: <https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-requestmapping.html>
- Type Conversion: <https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/typeconversion.html>
- `@ModelAttribute`: <https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/modelattrib-method-args.html>
- `@InitBinder`: <https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-initbinder.html>
- Validation: <https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-validation.html>
- Exceptions: <https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-exceptions.html>
- Controller Advice: <https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-advice.html>
- Error Responses: <https://docs.spring.io/spring-framework/reference/web/webflux/ann-rest-exceptions.html>
- Multipart Content: <https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/multipart-forms.html>
- Jackson JSON: <https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/jackson.html>
- RFC 9457 — Problem Details for HTTP APIs: <https://www.rfc-editor.org/rfc/rfc9457.html>
- Spring Boot — JSON (Jackson): <https://docs.spring.io/spring-boot/reference/features/json.html>
- Spring Boot — Validation: <https://docs.spring.io/spring-boot/reference/io/validation.html>

➡️ Siguiente: [2. Caso práctico: pedidos con validación reactiva vs. imperativa](02-pedidos-validacion-reactiva.md)
