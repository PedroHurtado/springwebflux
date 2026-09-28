# Día 1 — Fundamentos reactivos y arquitectura de WebFlux

**Lunes 28 de septiembre de 2026 · 09:30 – 14:30**

## Objetivos del día

Al terminar la sesión el alumno será capaz de:

1. Explicar qué problema resuelve WebFlux y cuándo conviene (y cuándo no) frente a Spring MVC.
2. Describir el contrato de Reactive Streams y el mecanismo de *backpressure*.
3. Crear y combinar `Mono` y `Flux` con los operadores más habituales, gestionar errores y elegir un `Scheduler`.
4. Identificar las capas del núcleo reactivo: `HttpHandler` → `WebHandler` (filtros, excepciones) → `DispatcherHandler`.
5. Seguir el recorrido de una petición por el `DispatcherHandler` y sus beans especiales.
6. Escribir un controlador anotado reactivo con respuestas JSON, NDJSON y Server-Sent Events.

## Agenda

| Horario | Bloque | Material |
|---|---|---|
| 09:30 – 09:50 | Presentación y entorno | [Preparación del entorno](#preparación-del-entorno) |
| 09:50 – 10:40 | Spring WebFlux: visión general | [01-vision-general.md](01-vision-general.md) |
| 10:40 – 11:40 | Bibliotecas reactivas (I): Reactive Streams y Reactor | [02-bibliotecas-reactivas.md](02-bibliotecas-reactivas.md) |
| 11:40 – 12:00 | *Descanso* | |
| 12:00 – 12:45 | Núcleo reactivo | [03-nucleo-reactivo.md](03-nucleo-reactivo.md) |
| 12:45 – 13:15 | DispatcherHandler | [04-dispatcherhandler.md](04-dispatcherhandler.md) |
| 13:15 – 14:15 | Controladores anotados (I) + laboratorio | [05-controladores-anotados.md](05-controladores-anotados.md) · [06-laboratorio.md](06-laboratorio.md) |
| 14:15 – 14:30 | Repaso y autoevaluación | [Preguntas de repaso](#preguntas-de-repaso) |

## Preparación del entorno

```bash
java -version          # 17 o superior
cd examples/day-01
./mvnw test            # Windows: mvnw.cmd test  -> 43 tests en verde
./mvnw spring-boot:run # arranca en http://localhost:8080
```

Abre <http://localhost:8080> en Chrome: verás los precios cambiando en tiempo real (Server-Sent Events).
Instrucciones completas del ejemplo: [examples/day-01/README.md](../../examples/day-01/README.md).

## Mapa del ejemplo del día

| Concepto | Dónde verlo en `examples/day-01` |
|---|---|
| Event loop vs. bloqueo | `demo/ThreadingDemoController.java` |
| `Mono`/`Flux`, operadores, errores | `src/test/.../reactor/A_*` … `C_*` |
| Backpressure, schedulers, frío/caliente, tiempo virtual | `src/test/.../reactor/D_*`, `E_*` |
| Adaptadores (`ReactiveAdapterRegistry`, `java.util.concurrent.Flow`) | `src/test/.../reactor/F_ReactiveAdaptersTest.java` |
| `HttpHandler` y `WebHandler` sin Spring Boot | `core/RawHttpHandlerServer.java` |
| `WebFilter` | `core/TimingWebFilter.java` |
| Beans especiales del `DispatcherHandler` | `core/DispatcherInfoController.java` → `GET /internals/dispatcher` |
| Controlador anotado reactivo (CRUD, NDJSON, SSE) | `catalog/ProductController.java` |
| Pruebas HTTP con `WebTestClient` | `src/test/.../catalog/ProductControllerTest.java` |

## Preguntas de repaso

1. ¿Por qué un servidor WebFlux funciona con tan pocos hilos? ¿Qué pasa si uno de ellos se bloquea?
2. ¿Qué cuatro interfaces define Reactive Streams y qué método permite la *backpressure*?
3. ¿Qué diferencia hay entre `map` y `flatMap`? ¿Y entre `flatMap` y `concatMap`?
4. ¿Qué ocurre si declaras un `Flux` y nadie se suscribe?
5. ¿Cuándo usarías `subscribeOn(Schedulers.boundedElastic())`?
6. Ordena de fuera hacia dentro: `DispatcherHandler`, `HttpHandler`, `WebFilter`, servidor Netty.
7. ¿Qué tres tipos de beans especiales consulta el `DispatcherHandler`?
8. ¿Qué devuelve `GET /api/products` con `Accept: application/json` y con `Accept: application/x-ndjson`?

<details>
<summary>Respuestas</summary>

1. Porque ningún hilo espera por E/S: las operaciones se registran y el hilo vuelve al *event loop* para atender otros eventos. Si uno se bloquea, todas las conexiones asignadas a ese hilo se quedan sin atender (ver `/api/demo/blocking`).
2. `Publisher`, `Subscriber`, `Subscription`, `Processor`. La *backpressure* se implementa con `Subscription.request(n)`.
3. `map` transforma de forma síncrona 1 a 1; `flatMap` transforma cada elemento en un `Publisher` y se suscribe a ellos concurrentemente (sin garantizar orden). `concatMap` se suscribe de uno en uno y conserva el orden.
4. Nada: *nothing happens until you subscribe*. En WebFlux quien se suscribe es el framework al escribir la respuesta.
5. Para aislar una llamada bloqueante inevitable (JDBC, SDK síncrono, fichero) fuera del *event loop*.
6. Servidor Netty → `HttpHandler` (adaptador) → `WebFilter`s (cadena del `WebHandler`) → `DispatcherHandler`.
7. `HandlerMapping`, `HandlerAdapter`, `HandlerResultHandler`.
8. Con JSON, un array completo (`[...]`); con NDJSON, un objeto JSON por línea emitido en *streaming*.
</details>

## Referencias del día

Cada documento incluye su propia sección de referencias. Las esenciales:

- Spring WebFlux (referencia oficial): <https://docs.spring.io/spring-framework/reference/web/webflux.html>
- Reactor Reference Guide: <https://projectreactor.io/docs/core/release/reference/>
- Reactive Streams (especificación JVM): <https://github.com/reactive-streams/reactive-streams-jvm/blob/master/README.md>
- Ejercicios de Reactor (Lite Rx API Hands-on, oficial de Reactor): <https://github.com/reactor/lite-rx-api-hands-on>

➡️ Siguiente: [Día 2 — Modelos de programación del servidor](../day-02/README.md)
