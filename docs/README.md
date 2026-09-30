# Spring WebFlux — Guía del curso

| | |
|---|---|
| **Duración** | 20 horas (4 sesiones de 5 h) |
| **Fechas** | Del lunes 28 de septiembre al jueves 1 de octubre de 2026 |
| **Horario** | 09:30 – 14:30 (descanso de 20 min hacia las 11:40) |
| **Modalidad** | Virtual |
| **Formador** | Pedro Hurtado |
| **Objetivo** | Conocer y aplicar el módulo Spring WebFlux para hacer aplicaciones web reactivas. |

## Requisitos previos

- Conocimiento de Java y programación orientada a objetos.
- Bases de Spring Framework (inyección de dependencias, beans, Spring Boot).
- Nociones de programación asíncrona y concurrente.
- HTTP y conceptos web básicos (métodos, cabeceras, códigos de estado, JSON).

## Entorno de trabajo

| Herramienta | Versión usada en el curso |
|---|---|
| JDK | **Java 17** o superior (el código compila con `--release 17`) |
| Maven | 3.9+ (cada ejemplo incluye Maven Wrapper: `mvnw` / `mvnw.cmd`) |
| Spring Boot | **4.1.1** |
| Spring Framework (spring-webflux) | 7.0.9 |
| Project Reactor (reactor-core) | 3.8.7 |
| Servidor | Reactor Netty (por defecto en `spring-boot-starter-webflux`) |
| Editor | Visual Studio Code (Extension Pack for Java + Spring Boot Extension Pack) u otro IDE |
| Navegador | Google Chrome (DevTools → pestaña *Network* para ver SSE/streaming) |
| Opcional | Extensión [REST Client](https://marketplace.visualstudio.com/items?itemName=humao.rest-client) para VS Code, [hey](https://github.com/rakyll/hey) para pruebas de carga |

> Requisitos de plataforma de Spring Boot: <https://docs.spring.io/spring-boot/system-requirements.html>

## Estructura del repositorio

```text
docs/
  README.md            <- este documento (planificación del curso)
  day-01/              <- teoría + laboratorio del día 1
  day-02/
  day-03/
  day-04/
examples/
  day-01/              <- proyecto Spring Boot del día 1 (catálogo reactivo)
  day-02/              <- día 2: catálogo + pedidos (Order -> OrderDetail -> Product)
  day-03/              <- día 3: + BFF con WebClient, caché HTTP, HTTP/2
  day-04/              <- día 4: + datos en H2 (R2DBC), vistas Thymeleaf, WebSockets, pruebas
```

Todos los ejemplos trabajan sobre **el mismo dominio: un catálogo de productos reactivo**. Cada día parte del
proyecto del día anterior y añade lo que se ve en la sesión, de modo que al final del curso se tiene una
aplicación completa.

## Distribución del temario

El temario sigue el mismo orden que la documentación oficial de
[Spring WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux.html), salvo *Bibliotecas
reactivas*, que se adelanta al día 1 porque Reactor (`Mono`/`Flux`) es imprescindible para todo lo demás.

### Día 1 — Lunes 28/09 · Fundamentos reactivos y arquitectura de WebFlux

| Horario | Bloque | Tema del temario |
|---|---|---|
| 09:30 – 09:50 | Presentación, entorno, importar `examples/day-01` | — |
| 09:50 – 10:40 | ¿Por qué reactivo? Modelo de hilos, event loop, MVC vs WebFlux | **Spring WebFlux: visión general** |
| 10:40 – 11:40 | Reactive Streams, backpressure, Reactor (`Mono`, `Flux`, operadores, errores, schedulers, adaptadores) | **Bibliotecas reactivas** (I) |
| 11:40 – 12:00 | *Descanso* | |
| 12:00 – 12:45 | `HttpHandler`, `WebHandler`, `WebFilter`, `ServerWebExchange`, codecs, streaming | **Núcleo reactivo** |
| 12:45 – 13:15 | Front controller, beans especiales, flujo de procesamiento, gestión de resultados | **DispatcherHandler** |
| 13:15 – 14:15 | `@RestController` reactivo, `Mono`/`Flux` como retorno, `@RequestBody` reactivo, SSE y NDJSON | **Controladores anotados** (I) |
| 14:15 – 14:30 | Repaso, preguntas, avance del día 2 | — |

➡️ [Teoría y laboratorio del día 1](day-01/README.md) · [Ejemplo del día 1](../examples/day-01/README.md)

### Día 2 — Martes 29/09 · Modelos de programación del servidor

| Horario | Bloque | Tema del temario |
|---|---|---|
| 09:30 – 09:45 | Repaso del día 1, importar `examples/day-02` | — |
| 09:45 – 10:45 | Mapeo avanzado, argumentos, conversión de tipos, *data binding*, validación, `@ExceptionHandler`, `@ControllerAdvice`, *Error Responses* (`ProblemDetail`, RFC 9457), multipart, Jackson | **Controladores anotados** (II) |
| 10:45 – 11:40 | Caso práctico: dominio de pedidos (`Order` → `OrderDetail` → `Product`) y validación de negocio reactiva frente a la imperativa de Spring MVC | **Controladores anotados** (II) · **Bibliotecas reactivas** |
| 11:40 – 12:00 | *Descanso* | |
| 12:00 – 12:50 | `RouterFunction`, `HandlerFunction`, `ServerRequest`/`ServerResponse`, predicados, rutas anidadas, filtros, errores, validación manual | **Puntos finales funcionales** |
| 12:50 – 13:10 | `UriComponentsBuilder`, codificación de URIs, `UriBuilderFactory`, URIs relativas a la petición, cabeceras `Forwarded` | **URI's** |
| 13:10 – 13:35 | Política del mismo origen, *preflight*, `@CrossOrigin`, configuración global y `CorsWebFilter` | **CORS** |
| 13:35 – 14:05 | `WebFluxConfigurer` (conversión, codecs, recursos estáticos, *path matching*), propiedades de Boot, *API versioning* (novedad Spring 7) | **Configuración de WebFlux** |
| 14:05 – 14:30 | Cierre del laboratorio, repaso y autoevaluación | — |

➡️ [Teoría y laboratorio del día 2](day-02/README.md) · [Ejemplo del día 2](../examples/day-02/README.md)

### Día 3 — Miércoles 30/09 · Cliente reactivo, seguridad y rendimiento HTTP

| Horario | Bloque | Tema del temario |
|---|---|---|
| 09:30 – 09:45 | Repaso del día 2, importar `examples/day-03` | — |
| 09:45 – 10:35 | Spring Security reactivo: `SecurityWebFilterChain`, `ReactiveUserDetailsService`, autorización por rutas y métodos, CSRF, cabeceras, OAuth2 Resource Server (JWT) | **Seguridad web** |
| 10:35 – 11:40 | Identidad entre microservicios: papel del IdP y de la gateway, *token relay*, *token exchange* (RFC 8693), *client credentials*, identidad a través de un broker | **Seguridad web** (II) |
| 11:40 – 12:00 | *Descanso* | |
| 12:00 – 13:15 | `WebClient`: configuración, `retrieve` vs `exchangeToMono`, cuerpos, filtros, atributos, `Context`, *timeouts* y reintentos, uso síncrono, *HTTP Service Client* (`@HttpExchange`); la cadena reactiva entre microservicios (BFF) sin bloquear | **WebClient** |
| 13:15 – 13:50 | `Cache-Control`, `ETag`, `Last-Modified`, peticiones condicionales, caché de recursos estáticos, *Range requests* | **Caché HTTP** |
| 13:50 – 14:15 | HTTP/2 en Reactor Netty (h2 y h2c), TLS con SSL bundles, `WebClient` sobre HTTP/2 | **HTTP/2** |
| 14:15 – 14:30 | Repaso y autoevaluación | — |

➡️ [Teoría y laboratorio del día 3](day-03/README.md) · [Ejemplo del día 3](../examples/day-03/README.md)

### Día 4 — Jueves 01/10 · Datos, tiempo real, vistas, pruebas y cierre

| Horario | Bloque | Tema del temario |
|---|---|---|
| 09:30 – 09:45 | Repaso del día 3, importar `examples/day-04` | — |
| 09:45 – 10:50 | Acceso a datos reactivo con R2DBC y H2: Spring Data R2DBC, `DatabaseClient`, agregados, transacciones reactivas, bloqueo optimista | **Bibliotecas reactivas** (II) |
| 10:50 – 11:40 | Thymeleaf reactivo: modelo reactivo, modo *data-driven*, `Rendering`, formularios; otras plantillas | **Tecnologías para las vistas** |
| 11:40 – 12:00 | *Descanso* | |
| 12:00 – 12:50 | `WebSocketHandler`, `WebSocketSession`, `SimpleUrlHandlerMapping`, `Sinks`, cliente WebSocket, comparación con SSE | **WebSockets** |
| 12:50 – 13:40 | `StepVerifier` avanzado, `TestPublisher`, `PublisherProbe`, `WebTestClient` (`bindTo...`), `@WebFluxTest`, `@DataR2dbcTest`, `@MockitoBean`, BlockHound | **Pruebas** |
| 13:40 – 14:00 | RxJava, `CompletableFuture`, corrutinas de Kotlin, `ReactiveAdapterRegistry`; `Context` de Reactor y propagación al MDC | **Bibliotecas reactivas** (II) |
| 14:00 – 14:30 | Cierre: repaso general, evaluación final, cuándo usar WebFlux, siguientes pasos (proyecto integrador para casa) | — |

➡️ [Teoría y laboratorio del día 4](day-04/README.md) · [Ejemplo del día 4](../examples/day-04/README.md) ·
[Cierre del curso](day-04/07-cierre-del-curso.md)

### Matriz de cobertura del temario

| # | Tema del temario | Día(s) | Documentación oficial |
|---|---|---|---|
| 1 | Spring WebFlux: visión general | 1 | [Overview](https://docs.spring.io/spring-framework/reference/web/webflux/new-framework.html) |
| 2 | Núcleo reactivo | 1 | [Reactive Core](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html) |
| 3 | DispatcherHandler | 1 | [DispatcherHandler](https://docs.spring.io/spring-framework/reference/web/webflux/dispatcher-handler.html) |
| 4 | Controladores anotados | 1 – 2 | [Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/webflux/controller.html) |
| 5 | Puntos finales funcionales | 2 | [Functional Endpoints](https://docs.spring.io/spring-framework/reference/web/webflux-functional.html) |
| 6 | URI's | 2 | [URI Links](https://docs.spring.io/spring-framework/reference/web/webflux/uri-building.html) |
| 7 | CORS | 2 | [CORS](https://docs.spring.io/spring-framework/reference/web/webflux-cors.html) |
| 8 | Seguridad web | 3 | [Web Security](https://docs.spring.io/spring-framework/reference/web/webflux/security.html) · [Spring Security Reactive](https://docs.spring.io/spring-security/reference/reactive/index.html) |
| 9 | Tecnologías para las vistas | 4 | [View Technologies](https://docs.spring.io/spring-framework/reference/web/webflux-view.html) |
| 10 | Caché HTTP | 3 | [HTTP Caching](https://docs.spring.io/spring-framework/reference/web/webflux/caching.html) |
| 11 | Configuración de WebFlux | 2 | [WebFlux Config](https://docs.spring.io/spring-framework/reference/web/webflux/config.html) |
| 12 | HTTP/2 | 3 | [HTTP/2](https://docs.spring.io/spring-framework/reference/web/webflux/http2.html) |
| 13 | WebClient | 3 | [WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html) |
| 14 | WebSockets | 4 | [WebSockets](https://docs.spring.io/spring-framework/reference/web/webflux-websocket.html) |
| 15 | Pruebas | 1 (intro) – 4 | [Testing](https://docs.spring.io/spring-framework/reference/web/webflux-test.html) · [WebTestClient](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html) |
| 16 | Bibliotecas reactivas | 1 – 4 (día 4: R2DBC, RxJava, `Context`) | [Reactive Libraries](https://docs.spring.io/spring-framework/reference/web/webflux-reactive-libraries.html) · [Reactor](https://projectreactor.io/docs/core/release/reference/) |

## Metodología

- Cada bloque combina **teoría breve → demostración en vivo → ejercicio** sobre el proyecto del día.
- Las pruebas se usan desde el primer día como herramienta de aprendizaje (`StepVerifier`, `WebTestClient`):
  cada concepto de Reactor tiene un test ejecutable en `examples/day-01/src/test`.
- Al final de cada día: repaso con preguntas de autoevaluación.

## Referencias generales

- Spring Framework — Web on Reactive Stack: <https://docs.spring.io/spring-framework/reference/web-reactive.html>
- Spring Boot — Reactive Web Applications: <https://docs.spring.io/spring-boot/reference/web/reactive.html>
- Project Reactor — Reference Guide: <https://projectreactor.io/docs/core/release/reference/>
- Project Reactor — Javadoc (diagramas de canicas de cada operador): <https://projectreactor.io/docs/core/release/api/>
- Reactive Streams: <https://www.reactive-streams.org/>
- Guía oficial "Building a Reactive RESTful Web Service": <https://spring.io/guides/gs/reactive-rest-service>
- Guía de migración a Spring Boot 4.0: <https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide>
- Notas de la versión Spring Boot 4.1: <https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes>
- Spring Initializr: <https://start.spring.io/>
