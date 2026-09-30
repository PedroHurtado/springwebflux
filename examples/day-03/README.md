# Día 3 — Cliente reactivo y rendimiento HTTP (Spring WebFlux)

Proyecto de ejemplo del [día 3](../../docs/day-03/README.md). Parte del proyecto del día 2 (catálogo + pedidos) y
añade:

- **WebClient y HTTP Service Clients**: un **BFF** (*Backend For Frontend*) que llama por HTTP al microservicio de
  pedidos y al de catálogo **sin bloquear**: composición con `flatMap`, llamadas en paralelo con concurrencia
  limitada, *streaming*, *timeouts*, reintentos, degradación y propagación de contexto (`X-Request-Id`) con el
  `Context` de Reactor.
- **Caché HTTP**: `ETag`, `Last-Modified`, `Cache-Control`, peticiones condicionales (304), caché de recursos
  estáticos y *Range requests* (206).
- **HTTP/2**: h2c por defecto y h2 con TLS (perfil `tls`).

La **seguridad** (Spring Security reactivo, identidad entre microservicios, gateway, IdP, brokers) se explica en
la documentación con fragmentos verificados, pero **no** se activa aquí, para poder lanzar todas las demos sin
tokens: [01 — Seguridad web](../../docs/day-03/01-seguridad-web.md) ·
[02 — Identidad entre microservicios](../../docs/day-03/02-identidad-entre-microservicios.md).

**Stack:** Java 17 · Spring Boot 4.1.1 · Spring Framework 7.0.9 · Reactor 3.8.7 · Reactor Netty 1.3.7 · Jackson 3

## Ejecutar

```bash
./mvnw test                 # Windows: mvnw.cmd test  (59 tests)
./mvnw spring-boot:run      # http://localhost:8080  (HTTP/1.1 + h2c)
./mvnw spring-boot:run -Dspring-boot.run.profiles=tls   # https://localhost:8443 (h2 + HTTP/1.1, certificado autofirmado)
```

- [`requests.http`](requests.http): las peticiones del día (BFF, caché, *Range*) listas para lanzar.
- <http://localhost:8080>: precios en tiempo real (SSE) y enlaces.

> Para `java -jar target/*.jar` hace falta Java 17+ en el `PATH` (con una versión anterior falla con
> `UnsupportedClassVersionError`).

## El BFF: una cadena de llamadas entre microservicios

```text
GET /api/bff/orders/{id}         X-Request-Id: abc   (o se genera uno)
  │
  ├─ CorrelationIdWebFilter      guarda "abc" en el Context de Reactor
  │
  └─ OrderSummaryService.summary
       ├─① OrdersApi.findById      (@HttpExchange sobre WebClient)  GET /api/orders/{id}        X-Request-Id: abc
       │     404 → OrderNotFoundException (404 ProblemDetail) · 5xx/sin conexión → 502 ProblemDetail
       │
       └─② CatalogClient.findProduct × N líneas, EN PARALELO (máx. 8)  GET /api/products/{id}  X-Request-Id: abc
             200 → AVAILABLE / OUT_OF_STOCK
             404 → DISCONTINUED
             5xx / timeout (800 ms) → 2 reintentos con backoff → si sigue fallando, UNKNOWN (el resumen NO falla)
       │
       └─③ reduce(OrderSummary.start(order), OrderSummary::add)   acumulador inmutable, sin collectList
```

- En el curso, pedidos y catálogo están **en esta misma aplicación**, pero el BFF los llama por HTTP con URLs
  configurables (`app.services.catalog.base-url`, `spring.http.serviceclient.orders.base-url`). Para separarlos
  en otras máquinas bastaría con cambiar las URLs.
- En los tests, los "otros microservicios" son un servidor Reactor Netty mínimo (`FakeRemoteServices`) con
  latencia y fallos controlados.

## Estructura (novedades del día 3)

```text
src/main/java/com/curso/webflux/day03/
├── client/                            Clientes HTTP de otros microservicios
│   ├── ClientConfig.java              WebClient del catálogo + @ImportHttpServices(group = "orders")
│   ├── CatalogClient.java             WebClient: retrieve, 404 -> vacío, timeout, retryWhen
│   ├── OrdersApi.java                 HTTP Service Client (@HttpExchange) de pedidos
│   ├── ProductDto.java / OrderDto.java  contratos vistos desde el cliente (tolerant reader)
│   ├── CorrelationId.java             cabecera y clave del Context
│   └── CorrelationIdPropagation.java  WebClientCustomizer + ExchangeFilterFunction (lee el Context)
├── bff/                               Backend For Frontend
│   ├── OrderSummaryController.java    /api/bff/orders/{id}, /{id}/lines (NDJSON), /{id}/blocking (anti-patrón)
│   ├── OrderSummaryService.java       la cadena reactiva: ① pedidos → ② catálogo en paralelo → ③ reduce
│   ├── OrderSummary.java              respuesta + acumulador inmutable del reduce
│   └── LineView.java                  línea enriquecida (AVAILABLE, OUT_OF_STOCK, DISCONTINUED, UNKNOWN)
├── core/CorrelationIdWebFilter.java   X-Request-Id -> Context de Reactor (y a la respuesta)
├── core/TimingWebFilter.java          ahora también registra el X-Request-Id en el log
├── catalog/ProductController.java     + ETag / Cache-Control en findById, image devuelve Resource (Range)
├── orders/OrderHandler.java           + ETag / Last-Modified / max-age en findById
└── error/GlobalExceptionHandler.java  + WebClientException -> 502 Bad Gateway
src/main/resources/
├── application.properties             URLs de servicios, timeouts de clientes, caché estática, HTTP/2
├── application-tls.properties         perfil tls: HTTPS (SSL bundle) + h2 en el puerto 8443
└── tls/demo.p12                       certificado AUTOFIRMADO solo para el curso (contraseña changeit)

src/test/java/com/curso/webflux/day03/
├── bff/FakeRemoteServices             pedidos y catálogo simulados (Reactor Netty HttpServer)
├── bff/OrderSummaryControllerTest     composición, paralelismo, Context, degradación, streaming, 404/502, block()
├── bff/OrderSummaryTest               el acumulador, sin Spring ni HTTP
├── caching/HttpCachingTest            304 por ETag y Last-Modified, recursos estáticos, Range (206)
├── http2/Http2Test                    h2c y HTTP/1.1 en el mismo puerto
├── http2/Http2TlsTest                 perfil tls: h2 negociado con ALPN
└── (los tests del día 2)              catálogo, pedidos, CORS, URIs
```

## Endpoints nuevos o modificados

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/bff/orders/{id}` | Pedido + estado actual de cada producto (llama a pedidos y catálogo) |
| GET | `/api/bff/orders/{id}/lines` | Las líneas enriquecidas en *streaming* (NDJSON) |
| GET | `/api/bff/orders/{id}/blocking` | ❌ Anti-patrón `block()` → 500 |
| GET | `/api/products/{id}` | + `ETag` y `Cache-Control: no-cache` (304 con `If-None-Match`) |
| GET | `/api/products/{id}/image` | Devuelve un `Resource`: admite `Range` (206) |
| GET | `/api/orders/{id}` | + `ETag`, `Last-Modified`, `Cache-Control: max-age=3600, private` |
| GET | `/index.html` y estáticos | `Cache-Control: max-age=600, public` + `Last-Modified` |

## Notas de Spring Boot 4 / Spring Framework 7

- **`spring-boot-starter-webclient`**: en Boot 4 la autoconfiguración de `WebClient` (el `WebClient.Builder`,
  los `WebClientCustomizer` y los HTTP Service Clients reactivos) ya no viene con `spring-boot-starter-webflux`.
- **`@ImportHttpServices`** (Spring Framework 7) registra interfaces `@HttpExchange` por grupos; Boot 4 los
  configura con `spring.http.serviceclient.<grupo>.*`. Hay que indicar `clientType = WEB_CLIENT`: por defecto
  se usa `RestClient` (bloqueante).
- **`spring.http.clients.*`** (`connect-timeout`, `read-timeout`, `ssl.bundle`...) configura todos los clientes
  HTTP que crea Boot.
- Los hilos del *event loop* se llaman `reactor-http-nio-N` al ejecutar la aplicación y `webflux-http-nio-N` en
  los tests con servidor real.
