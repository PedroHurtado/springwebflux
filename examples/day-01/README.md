# Día 1 — Catálogo reactivo (Spring WebFlux)

Proyecto de ejemplo del [día 1](../../docs/day-01/README.md): API REST reactiva de un catálogo de productos,
con demostraciones del modelo de hilos, del núcleo reactivo y del `DispatcherHandler`.

**Stack:** Java 17 · Spring Boot 4.1.1 · Spring Framework 7.0.9 · Reactor 3.8.7 · Reactor Netty · Jackson 3

## Ejecutar

```bash
./mvnw test                 # Windows: mvnw.cmd test  (43 tests)
./mvnw spring-boot:run      # http://localhost:8080
```

Servidor "a pelo" (sin Spring Boot, puertos 8081 y 8082):

```bash
./mvnw compile exec:java -Dexec.mainClass=com.curso.webflux.day01.core.RawHttpHandlerServer
```

## Estructura

```text
src/main/java/com/curso/webflux/day01/
├── Day01Application.java
├── catalog/                         Controladores anotados (bloque 5)
│   ├── Product.java                 record del dominio
│   ├── ProductRequest.java          DTO de entrada con Bean Validation
│   ├── ProductNotFoundException     @ResponseStatus(404)
│   ├── ProductRepository.java       repositorio en memoria con API Mono/Flux
│   ├── ProductService.java          composición con operadores, ticker de precios
│   └── ProductController.java       CRUD + NDJSON + SSE
├── core/                            Núcleo reactivo y DispatcherHandler (bloques 3 y 4)
│   ├── RawHttpHandlerServer.java    HttpHandler y WebHandler API sin Spring Boot
│   ├── TimingWebFilter.java         WebFilter: cabecera X-Response-Time + log
│   └── DispatcherInfoController     lista HandlerMapping / HandlerAdapter / HandlerResultHandler
└── demo/
    └── ThreadingDemoController      event loop: no bloqueante vs bloqueante vs boundedElastic
src/main/resources/static/index.html  cliente SSE (EventSource)

src/test/java/com/curso/webflux/day01/
├── reactor/                         Bibliotecas reactivas (bloque 2), un concepto por test
│   ├── A_MonoFluxBasicsTest         suscripción, just vs defer, señales, StepVerifier
│   ├── B_OperatorsTest              map, flatMap, concatMap, zip, merge, switchIfEmpty...
│   ├── C_ErrorHandlingTest          onErrorReturn/Resume/Map, retry, timeout, doFinally
│   ├── D_BackpressureAndSchedulersTest  request(n), limitRate, drop, publishOn, subscribeOn
│   ├── E_HotColdVirtualTimeTest     frío/caliente, Sinks, tiempo virtual
│   └── F_ReactiveAdaptersTest       ReactiveAdapterRegistry, CompletableFuture, java.util.concurrent.Flow
└── catalog/ProductControllerTest    WebTestClient: JSON, NDJSON, 404, CRUD, validación, SSE
```

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/products[?category=]` | Lista (JSON o NDJSON según `Accept`) |
| GET | `/api/products/{id}` | Un producto o 404 |
| GET | `/api/products/count` | Número de productos |
| POST | `/api/products` | Crea (201 + `Location`; 400 si no es válido) |
| PUT | `/api/products/{id}` | Actualiza (404 si no existe) |
| DELETE | `/api/products/{id}` | Borra (204 / 404) |
| GET | `/api/products/prices` | SSE: cambios de precio cada segundo |
| GET | `/api/demo/non-blocking?ms=` | Espera no bloqueante |
| GET | `/api/demo/blocking?ms=` | **Antipatrón**: `Thread.sleep` en el event loop |
| GET | `/api/demo/offloaded?ms=` | Bloqueo aislado en `boundedElastic` |
| GET | `/internals/dispatcher` | Beans especiales del `DispatcherHandler` |
| GET | `/actuator/mappings` | Rutas registradas por cada `HandlerMapping` |

Peticiones listas para usar en [`requests.http`](requests.http) (extensión REST Client de VS Code o
cliente HTTP de IntelliJ).

## Notas de Spring Boot 4

- Los *starters* de test son modulares: `spring-boot-starter-webflux-test` aporta `WebTestClient`.
- `@AutoConfigureWebTestClient` está en `org.springframework.boot.webtestclient.autoconfigure` y
  `@WebFluxTest` en `org.springframework.boot.webflux.test.autoconfigure`.
- Jackson 3 (`tools.jackson.*`) es el mapeador JSON por defecto.
