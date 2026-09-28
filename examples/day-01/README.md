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

## La carpeta `core`: lo que Spring Boot monta por debajo

`catalog` muestra el nivel más alto (un `@RestController`). La carpeta `core` enseña las capas que hay debajo
y que Spring Boot configura automáticamente:

```text
Netty ─► HttpHandler ─► WebFilter(s) ─► DispatcherHandler ─► @RestController (catalog)
          ①                ②                 ③
```

| Clase | Capa | Qué demuestra | Cómo se ejecuta |
|---|---|---|---|
| `RawHttpHandlerServer` | ① `HttpHandler` y WebHandler API | Servidor **sin Spring Boot**: puerto 8081 con un `HttpHandler` puro; puerto 8082 con `WebHandler` + `WebFilter` montados con `WebHttpHandlerBuilder` | Programa **independiente** con su propio `main` (ver abajo) |
| `TimingWebFilter` | ② `WebFilter` | Intercepta todas las peticiones: cabecera `X-Response-Time` y log con método, ruta, estado e hilo | Automático al arrancar la aplicación |
| `DispatcherInfoController` | ③ `DispatcherHandler` | Lista los `HandlerMapping`, `HandlerAdapter`, `HandlerResultHandler`, `WebFilter` y `WebExceptionHandler` reales con su orden | `GET /internals/dispatcher` |

### ① Servidor "a pelo": `RawHttpHandlerServer`

- **VS Code:** abrir `core/RawHttpHandlerServer.java` y pulsar **Run** encima del `main`.
- **Terminal:**

  ```bash
  ./mvnw compile exec:java -Dexec.mainClass=com.curso.webflux.day01.core.RawHttpHandlerServer
  ```

```bash
curl -i http://localhost:8081/prueba   # HttpHandler: responde con método, URI e hilo reactor-http-nio-*
curl -i http://localhost:8082/Pedro    # WebHandler + WebFilter: "Hola Pedro..." y cabecera X-Powered-By
```

No usa el puerto 8080, así que puede ejecutarse a la vez que la aplicación. Se para con `Ctrl+C`.

### ② y ③ Filtro e inspección del `DispatcherHandler`

Con la aplicación arrancada (`./mvnw spring-boot:run`):

```bash
curl -i http://localhost:8080/api/products/1      # cabecera X-Response-Time añadida por TimingWebFilter
curl http://localhost:8080/internals/dispatcher   # beans especiales del DispatcherHandler
```

En el log aparece una línea por petición escrita por el filtro:
`[b78eceb2-6] GET /api/products/1 -> 200 OK (77 ms, hilo reactor-http-nio-5)`.

Teoría relacionada: [3. Núcleo reactivo](../../docs/day-01/03-nucleo-reactivo.md) y
[4. DispatcherHandler](../../docs/day-01/04-dispatcherhandler.md).

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
