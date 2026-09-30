# Día 3 — Cliente reactivo, seguridad y rendimiento HTTP

**Miércoles 30 de septiembre de 2026 · 09:30 – 14:30**

## Objetivos del día

Al terminar la sesión el alumno será capaz de:

1. Proteger una aplicación WebFlux con Spring Security reactivo (`SecurityWebFilterChain`, reglas por ruta y por
   método, CSRF, cabeceras) y validar tokens JWT como **Resource Server**.
2. Explicar **dónde vive la identidad** en una aplicación reactiva (el `Context` de Reactor) y por qué no sirve un
   `ThreadLocal`.
3. Explicar el papel del **IdP** y de la **gateway**, y elegir cómo pasar la identidad del usuario de un
   microservicio a otro: *token relay*, *token exchange*, *client credentials*... y qué cambia cuando la
   llamada pasa por un **broker** de mensajería.
4. Consumir otros servicios con **`WebClient`** y con **HTTP Service Clients** (`@HttpExchange`): configuración,
   errores, filtros, `Context`, *timeouts*, reintentos y degradación.
5. Encadenar llamadas entre microservicios **sin romper la reactividad**: nada de `block()`, paralelismo con
   concurrencia limitada, *streaming* y propagación de contexto.
6. Aplicar **caché HTTP** (`Cache-Control`, `ETag`, `Last-Modified`, peticiones condicionales, recursos estáticos
   y *Range requests*).
7. Activar **HTTP/2** (h2c y h2 con TLS) en Reactor Netty y comprobar el protocolo negociado.

## Agenda

| Horario | Bloque | Material |
|---|---|---|
| 09:30 – 09:45 | Repaso del día 2 e importar `examples/day-03` | [Preparación del entorno](#preparación-del-entorno) |
| 09:45 – 10:35 | Seguridad web con Spring Security reactivo | [01-seguridad-web.md](01-seguridad-web.md) |
| 10:35 – 11:40 | Identidad entre microservicios: IdP, gateway, *token relay* / *exchange*, brokers | [02-identidad-entre-microservicios.md](02-identidad-entre-microservicios.md) |
| 11:40 – 12:00 | *Descanso* | |
| 12:00 – 13:15 | WebClient y la cadena reactiva entre microservicios | [03-webclient.md](03-webclient.md) |
| 13:15 – 13:50 | Caché HTTP | [04-cache-http.md](04-cache-http.md) |
| 13:50 – 14:15 | HTTP/2 | [05-http2.md](05-http2.md) |
| 14:15 – 14:30 | Repaso y autoevaluación | [Preguntas de repaso](#preguntas-de-repaso) |

El [laboratorio](06-laboratorio.md) está repartido: cada parte se hace al terminar su bloque de teoría (la parte A,
al terminar los bloques de seguridad).

> La seguridad (bloques 1 y 2) es **solo documentación**, con fragmentos de código verificados: el ejemplo del día
> no activa Spring Security para que las demos de WebClient, caché y HTTP/2 se puedan lanzar sin tokens. El
> mecanismo reactivo en el que se apoya la propagación de la identidad (el `Context` de Reactor hasta un
> `WebClient`) **sí** está en el ejemplo, con un `X-Request-Id`.

## Preparación del entorno

```bash
cd examples/day-03
./mvnw test            # Windows: mvnw.cmd test  -> 59 tests en verde
./mvnw spring-boot:run # http://localhost:8080 (HTTP/1.1 y h2c)
./mvnw spring-boot:run -Dspring-boot.run.profiles=tls   # https://localhost:8443 (h2, certificado autofirmado)
```

- `examples/day-03/requests.http`: todas las peticiones del día (extensión REST Client).
- `curl` 7.47+ para probar HTTP/2 (`--http2-prior-knowledge`).

Instrucciones completas del ejemplo: [examples/day-03/README.md](../../examples/day-03/README.md).

## Qué cambia respecto al día 2

```text
examples/day-02                          examples/day-03
catálogo (anotado)               ──►     + ETag / Cache-Control en findById, Range en la imagen
pedidos (funcional + anotado)    ──►     + ETag / Last-Modified / max-age en GET /api/orders/{id}
                                         client/  WebClient (CatalogClient), @HttpExchange (OrdersApi),
                                                  propagación de X-Request-Id (Context de Reactor)
                                         bff/     BFF que encadena pedidos → catálogo SIN bloquear
                                         core/    CorrelationIdWebFilter
                                         HTTP/2:  h2c por defecto, h2 con el perfil tls
```

## Novedades de Spring Boot 4 / Spring Framework 7 que aparecen hoy

Si se sigue un tutorial o una respuesta escrita para Spring Boot 3, estos puntos **no funcionan igual**:

| Qué | Antes (Boot 3 / Spring 6) | Ahora (Boot 4 / Spring 7) | Dónde se explica |
|---|---|---|---|
| `WebClient.Builder` autoconfigurado | Venía con `spring-boot-starter-webflux` | Hace falta **`spring-boot-starter-webclient`**; sin él, no hay bean `WebClient.Builder` | [3.3](03-webclient.md#33-crear-y-configurar-un-webclient) |
| Registrar interfaces `@HttpExchange` | `HttpServiceProxyFactory` a mano, un bean por interfaz | **`@ImportHttpServices`** por grupos. Hay que indicar **`clientType = WEB_CLIENT`**; si no, usa `RestClient` (bloqueante) | [3.9](03-webclient.md#39-http-service-client-httpexchange) |
| Configuración de los clientes HTTP | `spring.http.reactiveclient.*` (Boot 3.5) | **`spring.http.clients.*`** (todos los clientes) y **`spring.http.serviceclient.<grupo>.*`** (cada grupo) | [3.3](03-webclient.md#33-crear-y-configurar-un-webclient), [3.9](03-webclient.md#39-http-service-client-httpexchange) |
| *Starters* de OAuth2 | `spring-boot-starter-oauth2-resource-server` / `-oauth2-client` | **`spring-boot-starter-security-oauth2-resource-server`** / **`-security-oauth2-client`** y **`spring-boot-starter-security-test`** | [1.2](01-seguridad-web.md#12-arquitectura-un-webfilter-delante-de-todo) |
| `WebClient.exchange()` | Obsoleto | **Eliminado**: se usa `exchangeToMono` / `exchangeToFlux` | [3.4](03-webclient.md#34-retrieve-frente-a-exchangetomono) |
| `RestTemplate` | Cliente síncrono habitual | En retirada en favor de `RestClient` | [3.1](03-webclient.md#31-qué-es-webclient-y-cuándo-usarlo) |
| Spring Cloud Gateway (WebFlux) | Propiedades `spring.cloud.gateway.routes` | **`spring.cloud.gateway.server.webflux.routes`** | [2.4](02-identidad-entre-microservicios.md#24-cómo-se-implementa-en-webflux-sin-bloquear) |

Arrastrados de días anteriores y que siguen aplicando: *starters* de test modulares
(`spring-boot-starter-webflux-test`), Jackson 3 (`tools.jackson.*`) y `HttpStatus.UNPROCESSABLE_CONTENT`.

## Mapa del ejemplo del día

| Concepto | Dónde verlo en `examples/day-03` |
|---|---|
| **Resumen: cómo se configura WebClient y cómo pasaría el token entre microservicios** | [03-webclient.md — El recorrido en 3 pasos](03-webclient.md#el-recorrido-en-3-pasos-configuración-contexto-y-credenciales) |
| El `Context` de Reactor en lugar de `ThreadLocal` (mismo mecanismo que el `SecurityContext`) | `core/CorrelationIdWebFilter.java` (escribe), `client/CorrelationIdPropagation.java` (lee) |
| `WebClient` desde el `WebClient.Builder` de Boot | `client/ClientConfig.java` |
| `retrieve()`, 404 → vacío, `timeout`, `retryWhen` | `client/CatalogClient.java` |
| HTTP Service Client (`@HttpExchange`, `@ImportHttpServices`) | `client/OrdersApi.java`, `client/ClientConfig.java`, `application.properties` |
| DTO de cliente (*tolerant reader*) | `client/ProductDto.java`, `client/OrderDto.java` |
| Cadena de llamadas sin bloquear (`flatMap`, `flatMapSequential`, `reduce`) | `bff/OrderSummaryService.summary`, `bff/OrderSummary.java` (acumulador) |
| La misma cadena en *streaming* (NDJSON) | `bff/OrderSummaryService.lines` |
| Anti-patrón `block()` en el *event loop* | `bff/OrderSummaryService.summaryBlocking` → `/api/bff/orders/{id}/blocking` |
| Degradación ante fallos de otro servicio | `bff/OrderSummaryService.enrich`, `bff/LineView.java` (`UNKNOWN`, `DISCONTINUED`) |
| Errores remotos → `ProblemDetail` propio (404, 502) | `bff/OrderSummaryService.findOrder`, `error/GlobalExceptionHandler.handleUpstream` |
| Microservicios simulados en los tests | `src/test/.../bff/FakeRemoteServices.java` (Reactor Netty `HttpServer`) |
| `ETag` + `Cache-Control: no-cache` (anotado) | `catalog/ProductController.findById` |
| `ETag` + `Last-Modified` + `max-age` (funcional) | `orders/OrderHandler.findById` |
| Caché de recursos estáticos | `application.properties` (`spring.web.resources.cache.*`) |
| *Range requests* (206) | `catalog/ProductController.image` (devuelve un `Resource`) |
| HTTP/2 h2c y h2 (TLS, ALPN) | `application.properties`, `application-tls.properties`, `tls/demo.p12` |

## Preguntas de repaso

1. ¿Por qué `SecurityContextHolder` (MVC) no sirve en WebFlux? ¿Dónde guarda Spring Security el usuario autenticado?
2. ¿Qué diferencia hay entre un 401 y un 403? ¿Qué cabecera acompaña al 401 de un Resource Server?
3. ¿Por qué se desactiva CSRF en una API con tokens *Bearer* y no en una aplicación con sesión?
4. ¿Qué valida un Resource Server en un JWT? ¿Por qué importa el claim `aud` entre microservicios?
5. ¿Qué papel tienen el IdP y la gateway? ¿Por qué la gateway no debe ser el único punto de autorización?
6. Compara *token relay* y *token exchange*. ¿Cuándo usarías *client credentials*?
7. ¿Por qué no se debe meter el *access token* del usuario en un mensaje de Kafka? ¿Qué viaja en su lugar?
8. En `OrderSummaryService.summary`, ¿qué ocurriría con el `X-Request-Id` (o con el token) si en lugar de devolver
   el `Mono` se hiciera `subscribe()` dentro del servicio?
9. ¿Por qué `CatalogClient` pone `timeout` **antes** de `retryWhen`? ¿Qué errores reintenta y cuáles no?
10. ¿Qué diferencia hay entre `flatMap`, `flatMapSequential` y `concatMap` en el BFF?
11. ¿Qué significa `Cache-Control: no-cache`? ¿Y `private`?
12. ¿Por qué la imagen del producto admite `Range` y antes no?
13. ¿Qué diferencia hay entre h2 y h2c? ¿Por qué Chrome muestra `http/1.1` en `http://localhost:8080` si el
    servidor tiene HTTP/2 activado?

<details>
<summary>Respuestas</summary>

1. Porque guarda el usuario en un `ThreadLocal` y, en WebFlux, una petición pasa por varios hilos y cada hilo
   atiende muchas peticiones. Spring Security reactivo lo guarda en el **`Context` de Reactor** de la suscripción
   y se lee con `ReactiveSecurityContextHolder.getContext()`.
2. 401: no se sabe quién eres (sin token, caducado, firma inválida). 403: se sabe, pero no tienes permiso. El 401
   lleva `WWW-Authenticate: Bearer` (con detalles del error si el token era inválido).
3. CSRF explota que el navegador envía **solo** las cookies. Un token *Bearer* lo añade el código, no el navegador,
   así que no hay nada que explotar. Con una sesión en cookie el ataque es posible y hay que mantener la protección.
4. Firma (con las claves públicas del IdP), `exp`/`nbf`, `iss` y, si se configura, `aud`. Sin `aud`, un token
   emitido para el servicio A sería aceptado por el servicio B: un servicio comprometido podría reutilizar los
   tokens que recibe contra todos los demás.
5. IdP: autentica y emite tokens. Gateway: punto de entrada (TLS, enrutado, *rate limiting*), valida o hace el
   login (BFF), limpia cabeceras de identidad y reenvía o traduce tokens. No debe ser el único punto porque la red
   interna no es de confianza (*zero trust*): cada servicio valida el token y aplica su autorización fina.
6. *Relay*: se reenvía el mismo token; simple, pero el token necesita todas las audiencias (sobreprivilegio).
   *Exchange*: el servicio canjea el token en el IdP por otro con la audiencia y *scopes* del siguiente salto
   (mínimo privilegio, auditoría con `act`). *Client credentials*: cuando no hay usuario (*batch*, eventos) o el
   destino no necesita verificarlo (datos no personales).
7. Porque el mensaje se procesa tarde (el token habrá caducado), lo leen varios consumidores y se **almacena**:
   sería una credencial reutilizable por cualquiera con acceso al *topic*. Viaja la identidad como **dato**
   (`sub`, *tenant*, tipo de principal; opcionalmente firmado); productor y consumidor se autentican ante el broker
   con su propia identidad y el broker aplica ACL.
8. `subscribe()` crea una suscripción **nueva**, sin el `Context` de la petición: las llamadas saldrían sin
   `X-Request-Id` (o sin token → 401). Además el error no llegaría al cliente y la respuesta no esperaría al resultado.
9. Para que el límite de 800 ms se aplique a **cada intento**; detrás, limitaría el tiempo total incluidos los
   reintentos. Reintenta 5xx, *timeouts* y errores de conexión (transitorios); no reintenta 4xx (un 404 o un 401
   no cambian por repetir).
10. `flatMap`: todas las llamadas a la vez, resultados en orden de llegada (el *streaming* de `/lines`).
    `flatMapSequential`: todas a la vez, resultados en el orden original (el resumen). `concatMap`: de una en una
    (N × latencia).
11. `no-cache`: se puede guardar, pero hay que **revalidar** (`If-None-Match`) antes de usarlo. `private`: solo la
    caché del navegador; un proxy o CDN compartido no debe guardarlo.
12. Porque ahora devuelve un `Resource` (antes `byte[]`): `ResourceHttpMessageWriter` atiende `Range` y responde
    206 con `Content-Range`.
13. h2 va sobre TLS y se negocia con ALPN; h2c va en claro (*prior knowledge* o `Upgrade`). Los navegadores solo
    usan HTTP/2 sobre TLS, así que en `http://` siempre usan HTTP/1.1; con el perfil `tls` verás `h2`.
</details>

## Referencias del día

Cada documento incluye su propia sección de referencias. Las esenciales:

- Web Security (WebFlux): <https://docs.spring.io/spring-framework/reference/web/webflux/security.html>
- Spring Security — Reactive Applications: <https://docs.spring.io/spring-security/reference/reactive/index.html>
- RFC 8693 — OAuth 2.0 Token Exchange: <https://www.rfc-editor.org/rfc/rfc8693>
- WebClient: <https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html>
- HTTP Service Client: <https://docs.spring.io/spring-framework/reference/web/webflux-http-service-client.html>
- HTTP Caching: <https://docs.spring.io/spring-framework/reference/web/webflux/caching.html>
- HTTP/2: <https://docs.spring.io/spring-framework/reference/web/webflux/http2.html>

⬅️ [Día 2](../day-02/README.md) · ➡️ Siguiente: [Día 4 — Tiempo real, vistas, pruebas y cierre](../day-04/README.md)
