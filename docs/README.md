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
  day-0N/              <- cada día evoluciona el mismo dominio (catálogo de productos)
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

| Bloque | Tema del temario |
|---|---|
| Mapeo avanzado, argumentos y retornos, conversión de tipos, *data binding*, validación, `@ExceptionHandler`, `@ControllerAdvice`, *Error Responses* (`ProblemDetail`, RFC 9457), multipart, Jackson | **Controladores anotados** (II) |
| `RouterFunction`, `HandlerFunction`, `ServerRequest`/`ServerResponse`, predicados, rutas anidadas, filtros, validación manual | **Puntos finales funcionales** |
| `UriComponentsBuilder`, `UriBuilderFactory`, codificación de URIs, enlaces a controladores | **URI's** |
| CORS por anotación (`@CrossOrigin`), configuración global y `CorsWebFilter` | **CORS** |
| `@EnableWebFlux`, `WebFluxConfigurer`: conversión, validación, codecs (límites de memoria), *view resolvers*, recursos estáticos, *path matching*, *API versioning* (novedad Spring 7) | **Configuración de WebFlux** |

### Día 3 — Miércoles 30/09 · Cliente reactivo, seguridad y rendimiento HTTP

| Bloque | Tema del temario |
|---|---|
| `WebClient`: configuración, `retrieve` vs `exchange`, cuerpos, filtros, atributos, `Context`, timeouts y reintentos, uso síncrono, *HTTP Service Client* (`@HttpExchange`) | **WebClient** |
| Spring Security reactivo: `SecurityWebFilterChain`, `ReactiveUserDetailsService`, autorización por rutas y métodos, CSRF, cabeceras de seguridad, JWT / OAuth2 Resource Server | **Seguridad web** |
| `Cache-Control`, `ETag`, `Last-Modified`, peticiones condicionales, caché de recursos estáticos, *Range requests* | **Caché HTTP** |
| HTTP/2 en Reactor Netty (h2 y h2c), TLS, configuración en Spring Boot | **HTTP/2** |

### Día 4 — Jueves 01/10 · Tiempo real, vistas, pruebas y cierre

| Bloque | Tema del temario |
|---|---|
| Thymeleaf reactivo, `Rendering`, modo *data-driven* (`ReactiveDataDriverContextVariable`), otras plantillas | **Tecnologías para las vistas** |
| `WebSocketHandler`, `WebSocketSession`, mapeo con `SimpleUrlHandlerMapping`, cliente WebSocket, comparación con SSE | **WebSockets** |
| `StepVerifier` avanzado, tiempo virtual, `WebTestClient` (con servidor, *bind to controller/router*), `@WebFluxTest`, `@MockitoBean`, BlockHound | **Pruebas** |
| Interoperabilidad: RxJava, corrutinas de Kotlin, `ReactiveAdapterRegistry`; acceso a datos reactivo (R2DBC) como caso práctico; `Context` de Reactor | **Bibliotecas reactivas** (II) |
| Proyecto integrador, repaso general y evaluación | — |

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
| 16 | Bibliotecas reactivas | 1 – 4 | [Reactive Libraries](https://docs.spring.io/spring-framework/reference/web/webflux-reactive-libraries.html) · [Reactor](https://projectreactor.io/docs/core/release/reference/) |

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
