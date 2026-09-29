# Día 2 — Modelos de programación del servidor

**Martes 29 de septiembre de 2026 · 09:30 – 14:30**

## Objetivos del día

Al terminar la sesión el alumno será capaz de:

1. Enlazar parámetros de la petición a objetos (*data binding*), convertir tipos y validar entradas en un
   controlador anotado.
2. Centralizar la gestión de errores con `@ExceptionHandler` / `@ControllerAdvice` y responder con
   `ProblemDetail` (RFC 9457).
3. Escribir **validación de negocio reactiva** (consultas no bloqueantes, errores acumulados, ejecución en
   paralelo) y compararla con el equivalente imperativo de Spring MVC, con sus ventajas e inconvenientes.
4. Construir una API con **endpoints funcionales** (`RouterFunction`, `HandlerFunction`, filtros, gestión de
   errores y validación manual).
5. Construir y codificar URIs correctamente (`UriComponentsBuilder`, `UriBuilderFactory`).
6. Configurar CORS por anotación y de forma global, y entender el *preflight*.
7. Personalizar WebFlux con `WebFluxConfigurer` y propiedades de Spring Boot, incluido el **versionado de
   API** de Spring Framework 7.

## Agenda

| Horario | Bloque | Material |
|---|---|---|
| 09:30 – 09:45 | Repaso del día 1 e importar `examples/day-02` | [Preparación del entorno](#preparación-del-entorno) |
| 09:45 – 10:45 | Controladores anotados (II) | [01-controladores-anotados-ii.md](01-controladores-anotados-ii.md) |
| 10:45 – 11:40 | Caso práctico: pedidos con validación reactiva vs. imperativa | [02-pedidos-validacion-reactiva.md](02-pedidos-validacion-reactiva.md) |
| 11:40 – 12:00 | *Descanso* | |
| 12:00 – 12:50 | Puntos finales funcionales | [03-puntos-finales-funcionales.md](03-puntos-finales-funcionales.md) |
| 12:50 – 13:10 | URI's | [04-uris.md](04-uris.md) |
| 13:10 – 13:35 | CORS | [05-cors.md](05-cors.md) |
| 13:35 – 14:05 | Configuración de WebFlux (incluye *API versioning*) | [06-configuracion-webflux.md](06-configuracion-webflux.md) |
| 14:05 – 14:15 | Cierre del laboratorio | [07-laboratorio.md](07-laboratorio.md) |
| 14:15 – 14:30 | Repaso y autoevaluación | [Preguntas de repaso](#preguntas-de-repaso) |

El laboratorio está repartido: cada parte se hace al terminar su bloque de teoría.

## Preparación del entorno

```bash
cd examples/day-02
./mvnw test            # Windows: mvnw.cmd test  -> 43 tests en verde
./mvnw spring-boot:run # arranca en http://localhost:8080
```

- <http://localhost:8080>: precios en tiempo real (SSE) y enlaces a los endpoints nuevos.
- <http://127.0.0.1:8080/cors.html>: demo de CORS (se abre desde **otro origen**).
- `examples/day-02/requests.http`: todas las peticiones del día (extensión REST Client).

Instrucciones completas del ejemplo: [examples/day-02/README.md](../../examples/day-02/README.md).

## Qué cambia respecto al día 1

```text
examples/day-01                         examples/day-02
catálogo (@RestController)      ──►     catálogo (@RestController) + binding, validación, ProblemDetail,
                                          multipart, API versioning, @CrossOrigin
                                        pedidos (RouterFunction)  ← dominio NUEVO: Order → OrderDetail → Product
                                        error/  GlobalExceptionHandler (@RestControllerAdvice)
                                        config/ WebConfig (WebFluxConfigurer)
```

## Mapa del ejemplo del día

| Concepto | Dónde verlo en `examples/day-02` |
|---|---|
| *Data binding* a un record y conversión de tipos | `catalog/ProductSearch.java`, `config/StringToProductSortConverter.java` |
| Validación de `@RequestBody`, `@ModelAttribute` y `@RequestParam` | `catalog/ProductController.java` (`create`, `search`, `top`) |
| `@RestControllerAdvice` + `ProblemDetail` | `error/GlobalExceptionHandler.java`, `error/Problems.java` |
| Excepciones que llevan su `ProblemDetail` (`ErrorResponseException`) | `orders/OrderRejectedException.java`, `orders/OrderNotFoundException.java` |
| Multipart (`FilePart`) | `catalog/ProductController.java` (`uploadImage`), `catalog/ProductImageStore.java` |
| Jackson (anotaciones, propiedades de Boot) | `catalog/ProductV2.java`, `application.properties` |
| Dominio de pedidos y relación con `Product` | `orders/Order.java`, `orders/OrderDetail.java` |
| **Validación de negocio reactiva** | `orders/OrderService.java` (`create` vs. `createFailFast`), `orders/DetailCheck.java`, `orders/OrderValidation.java` (acumulador para `reduce`) |
| Pedidos con controlador **anotado** (comparación con MVC) | `orders/OrderController.java` → `/api/annotated/orders`, `OrderControllerTest` |
| Endpoints funcionales | `orders/OrderRouter.java`, `orders/OrderHandler.java` |
| URIs | `ProductController.create` (`UriComponentsBuilder`), `OrderHandler.create` (`request.uriBuilder()`), `src/test/.../uri/UriBuildingTest.java` |
| CORS | `config/WebConfig.java` (global), `@CrossOrigin` en `ProductController`, `static/cors.html`, `CorsTest` |
| `WebFluxConfigurer` y *API versioning* | `config/WebConfig.java`, `ProductController.findById` / `findByIdV2` |

## Preguntas de repaso

1. ¿Qué diferencia hay entre un error de *binding* y un error de validación? ¿Qué excepción produce cada uno en
   un `@ModelAttribute` y en un `@RequestParam` con `@Max`?
2. ¿Qué cinco campos estándar define un `ProblemDetail` (RFC 9457)? ¿Qué *content type* usa la respuesta?
3. ¿Por qué el `@RestControllerAdvice` del ejemplo no gestiona los errores de `/api/orders`?
4. ¿Por qué la validación de un pedido se divide en *estructural* (400) y *de negocio* (422)?
5. En `OrderService.create`, ¿qué pasaría si se cambiase `flatMapSequential` por `flatMap`? ¿Y por `concatMap`?
6. ¿Por qué `checkDetail` devuelve un `DetailCheck` en vez de emitir `Mono.error(...)`?
7. En los endpoints funcionales, ¿qué ocurre si una petición `POST /api/orders` llega con `Content-Type: text/plain`?
8. ¿Qué diferencia hay entre `.encode().buildAndExpand(vars)` y `.buildAndExpand(vars).encode()`?
9. ¿Cuándo envía el navegador una petición *preflight*? ¿Qué responde el servidor si el origen no está permitido?
10. ¿Por qué no se debe anotar `WebConfig` con `@EnableWebFlux` en una aplicación Spring Boot?

<details>
<summary>Respuestas</summary>

1. *Binding*: el valor no se puede **convertir** al tipo destino (`?maxPrice=abc`); validación: el valor se
   convierte, pero **incumple una restricción** (`?maxPrice=-5`). En un `@ModelAttribute` con `@Valid` ambos
   acaban en `WebExchangeBindException` (400); en un `@RequestParam @Max(10)` el error de restricción produce
   `HandlerMethodValidationException` (400) y el de conversión `ServerWebInputException` (400).
2. `type`, `title`, `status`, `detail`, `instance` (además de propiedades de extensión, como `errors`).
   *Content type*: `application/problem+json`.
3. Porque `@ControllerAdvice` solo se aplica a **controladores anotados**. Los endpoints funcionales gestionan
   sus errores en el propio `RouterFunction` (`onError`, `filter`) o en un `WebExceptionHandler`.
4. La estructural no necesita E/S y rechaza peticiones mal formadas (400 Bad Request). La de negocio necesita
   consultar el catálogo; la petición está bien formada pero no se puede procesar (422 Unprocessable Content).
5. Con `flatMap` las consultas seguirían en paralelo, pero los resultados llegarían **en orden de llegada** (los
   errores podrían salir desordenados). Con `concatMap` se consultaría **de una en una**: 5 líneas × 50 ms = 250 ms.
6. Porque `Mono.error` **corta** el flujo en el primer fallo; convirtiendo cada resultado en un valor
   (`Valid`/`Invalid`) se pueden reunir **todos** los errores y devolverlos juntos.
7. Ningún predicado coincide (`contentType(APPLICATION_JSON)`), así que no hay ruta: **404**. En un controlador
   anotado con `consumes` la respuesta sería 415.
8. La primera codifica la plantilla y luego codifica **estrictamente** los valores de las variables (un `/` o un
   `&` dentro del valor se codifican). La segunda solo codifica lo que es ilegal en cada componente, así que un
   `/` en una variable de ruta crea un segmento nuevo. Se recomienda la primera.
9. Cuando la petición no es "simple": métodos distintos de GET/HEAD/POST, cabeceras propias (`API-Version`) o
   `Content-Type` distinto de los de formulario (p. ej. `application/json`). Si el origen no está permitido
   el servidor responde **403** al preflight y el navegador no envía la petición real.
10. Porque `@EnableWebFlux` importa la configuración de WebFlux "a mano" y **desactiva la autoconfiguración** de
    Spring Boot (codecs, propiedades `spring.webflux.*`, recursos estáticos, conversores de Boot...).
</details>

## Referencias del día

Cada documento incluye su propia sección de referencias. Las esenciales:

- Annotated Controllers: <https://docs.spring.io/spring-framework/reference/web/webflux/controller.html>
- Error Responses: <https://docs.spring.io/spring-framework/reference/web/webflux/ann-rest-exceptions.html>
- Functional Endpoints: <https://docs.spring.io/spring-framework/reference/web/webflux-functional.html>
- URI Links: <https://docs.spring.io/spring-framework/reference/web/webflux/uri-building.html>
- CORS: <https://docs.spring.io/spring-framework/reference/web/webflux-cors.html>
- WebFlux Config: <https://docs.spring.io/spring-framework/reference/web/webflux/config.html>
- API Versioning: <https://docs.spring.io/spring-framework/reference/web/webflux-versioning.html>

⬅️ [Día 1](../day-01/README.md) · ➡️ Siguiente: [Día 3 — Cliente reactivo, seguridad y rendimiento HTTP](../day-03/README.md)
