# Día 4 — Tiempo real, vistas, pruebas y cierre (Spring WebFlux)

Proyecto de ejemplo del [día 4](../../docs/day-04/README.md). Parte del proyecto del día 3 (catálogo + pedidos + BFF)
y añade:

- **Acceso a datos reactivo con R2DBC sobre H2**: los repositorios en memoria se sustituyen por Spring Data R2DBC
  (`ProductRepository`) y `DatabaseClient` (`OrderRepository`, un agregado en dos tablas). Reserva de stock con un
  `UPDATE` condicional dentro de una **transacción reactiva**, y bloqueo optimista con `@Version` (que además da
  el `ETag`).
- **Vistas con Thymeleaf**: modo normal, modo *data-driven* (la página llega por trozos), `Rendering`, formularios
  con validación y Post/Redirect/Get.
- **WebSockets**: eco, precios en tiempo real con comandos del cliente (`switchMap`) y un chat entre sesiones
  (`Sinks`).
- **Otras bibliotecas reactivas**: RxJava (`Flowable`, `Single`) y `CompletableFuture` en controladores
  (`ReactiveAdapterRegistry`).
- **Pruebas**: `@DataR2dbcTest`, `@WebFluxTest` + `@MockitoBean`, `bindToController`, `bindToRouterFunction`,
  `StepVerifier` avanzado (`TestPublisher`, `PublisherProbe`, tiempo virtual, demanda), `Context` y **BlockHound**.

Lo que era solo del día 3 y no aporta hoy (HTTP/2 con TLS y sus tests) no se ha copiado.

**Stack:** Java 17 · Spring Boot 4.1.1 · Spring Framework 7.0.9 · Spring Data R2DBC 4.1.1 · r2dbc-h2 1.1.0 (H2 2.4) ·
Thymeleaf 3.1.5 · RxJava 3.1.12 · BlockHound 1.0.17 · Reactor 3.8.7 · Jackson 3

## Ejecutar

```bash
./mvnw test                 # Windows: mvnw.cmd test  (93 tests + 4 de BlockHound en una JVM aparte)
./mvnw spring-boot:run      # http://localhost:8080
```

- <http://localhost:8080>: índice con enlaces a todas las demos.
- <http://localhost:8080/catalog>: vistas Thymeleaf. <http://localhost:8080/ws.html>: WebSockets.
- [`requests.http`](requests.http): peticiones del día (secciones 4 a 6 son las nuevas).
- En el log se ven las sentencias SQL (`logging.level.org.springframework.r2dbc.core=DEBUG`).

> La base de datos es **H2 en memoria**: se crea al arrancar (`schema.sql` + `data.sql`) y se pierde al parar.
> No hay consola web de H2: es un *servlet* y esta aplicación no tiene contenedor de *servlets*.

## Estructura (novedades del día 4)

```text
src/main/java/com/curso/webflux/day04/
├── catalog/
│   ├── Product.java                   record + entidad (@Table, @Id, @Version -> ETag)
│   ├── ProductRepository.java         Spring Data R2DBC: heredadas, derivadas, @Query, @Modifying (reserveStock)
│   ├── ProductService.java            mismo contrato que antes; update con bloqueo optimista
│   └── PriceFeed.java                 ticker de precios CALIENTE (share) para SSE y WebSocket
├── orders/
│   ├── OrderRepository.java           DatabaseClient: INSERT cabecera + líneas, JOIN + bufferUntilChanged
│   └── OrderService.java              UPDATE condicional + TransactionalOperator (rollback)
├── web/                               Vistas (Thymeleaf)
│   ├── CatalogViewController.java     /catalog (full), /catalog/live (data-driven), /catalog/{id} (Rendering), formulario
│   └── ProductForm.java               objeto de formulario (JavaBean) con Bean Validation
├── ws/                                WebSockets
│   ├── WebSocketConfig.java           SimpleUrlHandlerMapping: /ws/echo, /ws/prices, /ws/chat
│   ├── EchoWebSocketHandler.java
│   ├── PriceWebSocketHandler.java     comandos JSON del cliente + switchMap sobre el PriceFeed
│   └── ChatWebSocketHandler.java      Sinks.many().multicast() entre sesiones
├── interop/InteropController.java     Flowable, Single, CompletableFuture
└── error/GlobalExceptionHandler.java  + OptimisticLockingFailureException -> 409
src/main/resources/
├── schema.sql / data.sql              tablas product, customer_order, order_detail y los 5 productos
├── templates/                         fragments.html, catalog/{list,live,detail,form,not-found}.html
├── static/ws.html                     demos WebSocket en el navegador
└── application.properties             + spring.r2dbc.*, spring.sql.init.*, spring.thymeleaf.*

src/test/java/com/curso/webflux/day04/
├── data/ProductRepositoryTest         @DataR2dbcTest: consultas, reserva atómica, bloqueo optimista
├── data/OrderPersistenceTest          agregado, rollback de la transacción, pedidos concurrentes
├── orders/OrderServiceTest            unitario con Mockito + PublisherProbe + tiempo virtual
├── web/CatalogViewTest                HTML, modo data-driven por trozos, 404 en página, formulario
├── ws/WebSocketTest                   ReactorNettyWebSocketClient contra el servidor real
├── ws/PriceWatchTest                  la lógica del WebSocket con TestPublisher
├── interop/InteropTest                bindToController + ReactiveAdapterRegistry
├── reactor/ContextTest                Context de Reactor: posición de contextWrite, hilos, StepVerifier
├── testing/ProductControllerSliceTest @WebFluxTest + @MockitoBean
├── testing/OrderRoutesUnitTest        bindToRouterFunction sin Spring
├── testing/StepVerifierAdvancedTest   tiempo virtual con interval, demanda, verifyThenAssertThat, checkpoint
├── testing/BlockHoundTest             @Tag("blockhound"): detectar un bloqueo escondido
├── testing/ApiDoesNotBlockTest        @Tag("blockhound"): la API completa no bloquea el event loop
└── (los tests de los días 2 y 3)      ahora contra H2
```

## Endpoints nuevos

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/catalog[?category=]` | Página HTML del catálogo (modo normal) |
| GET | `/catalog/live` | La misma tabla en modo *data-driven* (400 ms por fila) |
| GET | `/catalog/{id}` | Ficha del producto (`Rendering`); 404 como página HTML |
| GET / POST | `/catalog/new`, `/catalog` | Formulario de alta: errores en la página o 303 a la ficha |
| WS | `/ws/echo` | Eco |
| WS | `/ws/prices` | Cambios de precio filtrados por `{"productIds":[...]}` |
| WS | `/ws/chat?name=` | Chat entre todas las sesiones |
| GET | `/api/interop/rx/low-stock?max=` | `Flowable<Product>` (RxJava) |
| GET | `/api/interop/rx/products/{id}` | `Single<ProductV2>` (RxJava) |
| GET | `/api/interop/future/count` | `CompletableFuture<Long>` |

Cambios de comportamiento: `PUT /api/products/{id}` con una versión desfasada → **409**; el `ETag` de
`GET /api/products/{id}` es `"<id>-v<versión>"`; un pedido que pierde la carrera por el stock → **422**.

## Notas de Spring Boot 4 / Spring Data 4

- **`spring-boot-starter-data-r2dbc`** + el driver (`io.r2dbc:r2dbc-h2`). Sin `spring.r2dbc.url`, Boot crea una
  base de datos embebida; `spring.r2dbc.generate-unique-name=true` le da un nombre aleatorio por contexto.
- **`spring.sql.init.*`** ejecuta `schema.sql`/`data.sql` también con R2DBC (`mode=embedded` por defecto).
- **`@DataR2dbcTest`** está en `org.springframework.boot.data.r2dbc.test.autoconfigure`
  (*starter* `spring-boot-starter-data-r2dbc-test`); **`@WebFluxTest`** en
  `org.springframework.boot.webflux.test.autoconfigure`.
- **`@MockitoBean`** (Spring Framework) sustituye a `@MockBean` de Spring Boot, que ya no existe en Boot 4.
- **`@Table("product")`** con nombre explícito se entrecomilla en el SQL y H2 no encuentra `PRODUCT`: se usa `@Table`
  sin nombre (o el nombre en mayúsculas).
- BlockHound no lo gestiona Boot (versión fija en el `pom.xml`) y necesita `-XX:+AllowRedefinitionToAddDeleteMethods`
  en Java 13+.
