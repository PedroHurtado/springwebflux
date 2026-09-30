# Día 4 — Tiempo real, vistas, pruebas y cierre

**Jueves 1 de octubre de 2026 · 09:30 – 14:30**

## Objetivos del día

Al terminar la sesión el alumno será capaz de:

1. Acceder a una base de datos relacional **sin bloquear** con R2DBC (H2): repositorios de Spring Data R2DBC,
   `DatabaseClient`, agregados en varias tablas, **transacciones reactivas** y bloqueo optimista.
2. Generar **vistas HTML** con Thymeleaf en WebFlux: modelo reactivo, modo *data-driven*, `Rendering` y formularios
   con validación.
3. Implementar **WebSockets** (`WebSocketHandler`, `SimpleUrlHandlerMapping`, `Sinks`) y elegir entre WebSocket y SSE.
4. **Probar** una aplicación WebFlux a todos los niveles: `StepVerifier` avanzado, `TestPublisher`, `PublisherProbe`,
   `WebTestClient` (`bindTo...`), `@WebFluxTest`, `@DataR2dbcTest`, `@MockitoBean` y **BlockHound**.
5. Usar **otras bibliotecas reactivas** (RxJava, `CompletableFuture`, corrutinas de Kotlin) gracias al
   `ReactiveAdapterRegistry`, y dominar el **`Context`** de Reactor (incluida su propagación al MDC).
6. Decidir **cuándo** usar WebFlux y qué comprobar antes de llevarlo a producción.

## Agenda

| Horario | Bloque | Material |
|---|---|---|
| 09:30 – 09:45 | Repaso del día 3 e importar `examples/day-04` | [Preparación del entorno](#preparación-del-entorno) |
| 09:45 – 10:50 | Acceso a datos reactivo: R2DBC y H2 | [01-acceso-a-datos-r2dbc.md](01-acceso-a-datos-r2dbc.md) |
| 10:50 – 11:40 | Tecnologías para las vistas (Thymeleaf) | [02-vistas.md](02-vistas.md) |
| 11:40 – 12:00 | *Descanso* | |
| 12:00 – 12:50 | WebSockets | [03-websockets.md](03-websockets.md) |
| 12:50 – 13:40 | Pruebas | [04-pruebas.md](04-pruebas.md) |
| 13:40 – 14:00 | Bibliotecas reactivas (II): interoperabilidad y `Context` | [05-bibliotecas-reactivas-ii.md](05-bibliotecas-reactivas-ii.md) |
| 14:00 – 14:30 | Cierre del curso: repaso general, evaluación final, siguientes pasos | [07-cierre-del-curso.md](07-cierre-del-curso.md) |

El [laboratorio](06-laboratorio.md) está repartido: cada parte (A–E) se hace al terminar su bloque. El **proyecto
integrador** (reseñas de producto) recorre todo el curso y se deja para el final o para casa, con la solución
completa y verificada.

## Preparación del entorno

```bash
cd examples/day-04
./mvnw test            # Windows: mvnw.cmd test  -> 93 tests + 4 de BlockHound (en una JVM aparte)
./mvnw spring-boot:run # http://localhost:8080
```

- No hay que instalar ninguna base de datos: H2 va embebida y se crea al arrancar (`schema.sql`, `data.sql`).
- `examples/day-04/requests.http`: peticiones del día (secciones 4 a 6).
- <http://localhost:8080/catalog> (vistas) y <http://localhost:8080/ws.html> (WebSockets).

Instrucciones completas del ejemplo: [examples/day-04/README.md](../../examples/day-04/README.md).

## Qué cambia respecto al día 3

```text
examples/day-03                          examples/day-04
ProductRepository (Map en memoria) ──►   interfaz Spring Data R2DBC (H2): derivadas, @Query, @Modifying
OrderRepository  (Map en memoria)  ──►   DatabaseClient: 2 tablas, JOIN + bufferUntilChanged
OrderService.reserveStockAndSave   ──►   UPDATE condicional + TransactionalOperator (rollback)
ETag = hash del contenido          ──►   ETag = columna @Version (+ 409 por bloqueo optimista)
ticker de precios por suscriptor   ──►   PriceFeed compartido (share) para SSE y WebSocket
                                         web/      vistas Thymeleaf (full, data-driven, Rendering, formulario)
                                         ws/       WebSockets: eco, precios con comandos, chat
                                         interop/  RxJava y CompletableFuture en controladores
HTTP/2 con TLS (perfil tls)        ──►   no se copia (solo del día 3)
```

## Novedades de Spring Boot 4 / Spring Framework 7 que aparecen hoy

| Qué | Antes (Boot 3 / Spring 6) | Ahora (Boot 4 / Spring 7) | Dónde se explica |
|---|---|---|---|
| Dobles en el contexto de test | `@MockBean`, `@SpyBean` (Spring Boot) | **`@MockitoBean`, `@MockitoSpyBean`** (Spring Framework); los de Boot ya no existen | [4.6](04-pruebas.md#46-slices-de-spring-boot-webfluxtest-y-datar2dbctest) |
| Paquetes de los *slices* | `org.springframework.boot.test.autoconfigure.*` | `org.springframework.boot.webflux.test.autoconfigure.WebFluxTest`, `org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest` | [4.6](04-pruebas.md#46-slices-de-spring-boot-webfluxtest-y-datar2dbctest) |
| *Starters* de test | `spring-boot-starter-test` | Uno por módulo: `spring-boot-starter-data-r2dbc-test`, `-webflux-test`... | [examples/day-04/pom.xml](../../examples/day-04/pom.xml) |
| `Limit` en consultas derivadas | `Pageable` o `@Query` con `LIMIT` | `findAllByOrderByPriceDesc(Limit.of(n))` (Spring Data 3.2+) | [1.4](01-acceso-a-datos-r2dbc.md#14-el-repositorio-solo-una-interfaz) |
| WebSocket en servidores | Reactor Netty, Tomcat, Jetty, Undertow | Undertow ya no está soportado | [3.4](03-websockets.md#34-registrar-los-handlers) |

Arrastrados de días anteriores y que siguen aplicando: Jackson 3 (`tools.jackson.*`, el `JsonMapper` que usa el
WebSocket de precios), `spring-boot-starter-webclient`, `@ImportHttpServices` y `HttpStatus.UNPROCESSABLE_CONTENT`.

## Mapa del ejemplo del día

| Concepto | Dónde verlo en `examples/day-04` |
|---|---|
| Entidad R2DBC con `record`, `@Id`, `@Version` | `catalog/Product.java` |
| Consultas heredadas, derivadas, `@Query`, `@Modifying` | `catalog/ProductRepository.java` |
| Agregado en dos tablas con `DatabaseClient` | `orders/OrderRepository.java` (`save`, `toOrders`) |
| Transacción reactiva y reserva atómica | `orders/OrderService.reserveStockAndSave` |
| Bloqueo optimista → 409 · `ETag` desde la versión | `catalog/ProductService.update`, `error/GlobalExceptionHandler`, `catalog/ProductController.etag` |
| Inicialización de la BD y configuración | `schema.sql`, `data.sql`, `application.properties` |
| Vista con modelo reactivo (modo normal) | `web/CatalogViewController.list`, `templates/catalog/list.html` |
| Vista *data-driven* (por trozos) | `web/CatalogViewController.live`, `templates/catalog/live.html` |
| `Rendering` y página de error 404 | `web/CatalogViewController.detail` / `notFound` |
| Formulario con validación y Post/Redirect/Get | `web/CatalogViewController.create`, `web/ProductForm.java`, `templates/catalog/form.html` |
| Registro de WebSockets | `ws/WebSocketConfig.java` |
| Eco, comandos con `switchMap`, difusión con `Sinks` | `ws/EchoWebSocketHandler`, `ws/PriceWebSocketHandler`, `ws/ChatWebSocketHandler` |
| Publicador caliente compartido | `catalog/PriceFeed.java` |
| RxJava y `CompletableFuture` en controladores | `interop/InteropController.java` |
| Tests: unitario con Mockito, `PublisherProbe`, tiempo virtual | `orders/OrderServiceTest` |
| Tests: `@DataR2dbcTest`, *rollback*, concurrencia | `data/ProductRepositoryTest`, `data/OrderPersistenceTest` |
| Tests: `@WebFluxTest` + `@MockitoBean` | `testing/ProductControllerSliceTest` |
| Tests: `bindToRouterFunction`, `bindToController` | `testing/OrderRoutesUnitTest`, `interop/InteropTest` |
| Tests: `TestPublisher`, demanda, `checkpoint`, `Context` | `ws/PriceWatchTest`, `testing/StepVerifierAdvancedTest`, `reactor/ContextTest` |
| Tests: vistas y WebSockets de extremo a extremo | `web/CatalogViewTest`, `ws/WebSocketTest` |
| BlockHound | `testing/BlockHoundTest`, `testing/ApiDoesNotBlockTest`, `pom.xml` (surefire) |

## Preguntas de repaso

1. ¿Qué ganas usando R2DBC en lugar de JDBC en una aplicación WebFlux? ¿Qué pierdes respecto a JPA?
2. ¿Por qué `Product` lleva `@Version` si el id lo asigna la aplicación?
3. ¿Por qué `@Table("product")` hace fallar las consultas en H2?
4. ¿Cómo se lee un `Order` con sus líneas sin cargar la tabla entera en memoria?
5. ¿Por qué la reserva de stock es un `UPDATE ... WHERE stock >= :quantity` si la validación ya comprobó el stock?
6. ¿Dónde vive la transacción de R2DBC? ¿Qué pasa si dentro del servicio haces `subscribe()` de otra operación?
7. ¿Qué diferencia hay entre meter un `Flux` en el `Model` y meter una `ReactiveDataDriverContextVariable`?
8. ¿Por qué el formulario usa `ProductForm` (JavaBean) y no el `record ProductRequest`?
9. ¿Cuándo usarías WebSocket en lugar de SSE? ¿Qué aporta `switchMap` en el WebSocket de precios?
10. ¿Por qué el chat usa `Sinks.many().multicast().directBestEffort()` y `emitNext` con `busyLooping`?
11. ¿Qué diferencia hay entre `verify(mock).save(any())` de Mockito y `PublisherProbe.assertWasSubscribed()`?
12. ¿Qué arranca `@WebFluxTest` y qué no? ¿Qué sustituye a `@MockBean` en Boot 4?
13. ¿Por qué los tests de BlockHound se ejecutan en una JVM aparte? ¿Qué encontró BlockHound en las vistas?
14. ¿Por qué `CompletableFuture` no es un buen tipo para usar dentro de un pipeline?

<details>
<summary>Respuestas</summary>

1. Ganas E/S no bloqueante de extremo a extremo (el hilo no espera a la BD) y *backpressure* sobre las filas.
   Pierdes el ORM: relaciones, carga *lazy*, cascadas y caché de primer nivel; los agregados se cargan a mano.
2. Para el bloqueo optimista y para que Spring Data sepa si `save()` debe insertar (`version == null`) o actualizar:
   con un id no nulo no lo puede deducir del id. De paso, da un `ETag` que cambia con cada modificación.
3. Porque un nombre explícito se escribe entre comillas en el SQL y, entre comillas, H2 distingue mayúsculas: la
   tabla es `PRODUCT`. Se usa `@Table` sin nombre (o en mayúsculas).
4. Una consulta con `JOIN` ordenada por pedido y `bufferUntilChanged(orderId)`: se agrupan las filas consecutivas de
   cada pedido y se emite un `Order` por grupo. Solo se tienen en memoria las líneas de un pedido a la vez.
5. Porque entre la validación y la reserva otra petición puede llevarse el stock. El `UPDATE` condicional comprueba
   y descuenta en una sola operación atómica: si devuelve 0 filas, el pedido se rechaza (422).
6. En el `Context` de Reactor de la suscripción (la conexión transaccional). Un `subscribe()` crea otra suscripción,
   sin ese `Context`: se ejecuta fuera de la transacción, con otra conexión, y sus errores no llegan al cliente.
7. Un `Flux` en el `Model` se resuelve (a `List`) antes de renderizar: la página se genera de una vez. La variable
   *data-driven* dirige el renderizado: la página se envía por trozos según emite el `Flux`.
8. Porque el formulario tiene que repintarse con los valores que escribió el usuario aunque no sean válidos, y
   `th:field` lee y escribe propiedades JavaBean.
9. Cuando el cliente también envía mensajes a menudo por la misma conexión (chat, comandos, juegos). `switchMap`
   cancela la suscripción al feed con el filtro anterior y abre otra con el nuevo, sin estado mutable.
10. `multicast` para varios suscriptores; `directBestEffort` para que un cliente lento solo se pierda sus mensajes
    sin frenar a los demás. `busyLooping` porque varias sesiones emiten a la vez y `tryEmitNext` fallaría con
    `FAIL_NON_SERIALIZED`.
11. `verify` comprueba que se **llamó** al método; `PublisherProbe` que alguien se **suscribió** al `Mono` que
    devolvió, es decir, que se **ejecutó**. En `then(orders.save(order))` el método se llama siempre al montar el
    pipeline, pero solo se ejecuta si todo va bien.
12. Arranca la capa web (controladores indicados, `@ControllerAdvice`, `WebFluxConfigurer`, `WebFilter`, Jackson,
    validación, `WebTestClient`), no servicios, repositorios ni servidor. En Boot 4, `@MockitoBean` de Spring Framework.
13. Porque se instala para toda la JVM y no se puede desinstalar (y necesita un *flag* de la JVM). En las vistas,
    Thymeleaf lee las plantillas del disco de forma síncrona en el *event loop* (`FileInputStream#readBytes`).
14. Porque es impaciente (se ejecuta al crearlo, no al suscribirse), no se puede cancelar ni reintentar
    re-suscribiéndose y pierde el `Context`.
</details>

## Referencias del día

Cada documento incluye su propia sección de referencias. Las esenciales:

- Spring Framework — Data Access with R2DBC: <https://docs.spring.io/spring-framework/reference/data-access/r2dbc.html>
- Spring Data R2DBC: <https://docs.spring.io/spring-data/relational/reference/r2dbc.html>
- View Technologies (WebFlux): <https://docs.spring.io/spring-framework/reference/web/webflux-view.html>
- WebSockets (WebFlux): <https://docs.spring.io/spring-framework/reference/web/webflux-websocket.html>
- Testing (WebFlux): <https://docs.spring.io/spring-framework/reference/web/webflux-test.html>
- Reactive Libraries: <https://docs.spring.io/spring-framework/reference/web/webflux-reactive-libraries.html>
- BlockHound: <https://github.com/reactor/BlockHound>

⬅️ [Día 3](../day-03/README.md) · [Guía del curso](../README.md)
