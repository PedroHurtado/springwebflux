# 6. Laboratorio del día 3

> Proyecto: `examples/day-03` · Duración total aproximada: 75 min (repartidos entre los bloques)
>
> Todas las soluciones con código se han compilado y ejecutado contra una copia del proyecto del día (Spring Boot
> 4.1.1) con tests de integración (`WebTestClient`) o `StepVerifier`. La parte A2 se verificó añadiendo los
> *starters* de Spring Security 7.1.1.

## Parte 0 — Arranque (5 min)

```bash
cd examples/day-03
./mvnw test              # Windows: mvnw.cmd test   → 59 tests OK
./mvnw spring-boot:run
```

1. Lanza las peticiones de la sección 2 de `requests.http` (crear pedido → resumen del BFF). En el log, busca el
   `X-Request-Id` del resumen: ¿cuántas líneas de log lo contienen y por qué?
2. Lanza `GET /api/bff/orders/{id}/blocking`. Busca en el log el nombre del hilo que aparece en la excepción.

<details><summary>Respuestas</summary>

1. Una por petición: la del BFF, la llamada del BFF a `/api/orders/{id}` y una por cada línea a
   `/api/products/{id}` (con un pedido de 2 líneas, 4 en total). El BFF se llama a sí mismo por HTTP y `CorrelationIdPropagation` añade la cabecera a cada
   llamada saliente; `CorrelationIdWebFilter` la lee al recibirla y `TimingWebFilter` la escribe en el log.
2. `reactor-http-nio-N`: un hilo del *event loop* de Reactor Netty. Reactor prohíbe `block()` en esos hilos.
</details>

## Parte A — Seguridad e identidad (20 min, tras los bloques 1 y 2)

### A1. Elige la estrategia (sin código)

Para cada caso, ¿qué alternativa de [2.3](02-identidad-entre-microservicios.md#23-alternativas-para-pasar-la-identidad-entre-servicios-http)
usarías y por qué?

1. `orders-service` consulta a `catalog-service` el precio de un producto.
2. `orders-service` pide a `invoices-service` las facturas del cliente que ha iniciado sesión; `invoices-service`
   debe comprobar que el usuario solo ve **sus** facturas. Hay 30 microservicios y 6 equipos.
3. Una tarea programada nocturna recalcula el stock llamando a `catalog-service`.
4. `billing-service` consume `OrderPlaced` de Kafka dos horas después de publicarse y tiene que registrar quién
   hizo el pedido.
5. Un compañero propone que la gateway valide el JWT y pase a los servicios solo `X-User-Id` y `X-Roles`, "porque
   la red interna es nuestra".

<details><summary>Respuestas</summary>

1. **C. Client credentials**: el precio no depende del usuario; `catalog` autoriza al **servicio** (`catalog.read`).
2. **B. Token exchange**: el destino necesita **verificar** al usuario y, con 30 servicios, un token con todas las
   audiencias (*relay*) sería un riesgo de movimiento lateral. El IdP emite un token con `aud: invoices-service`,
   el `sub` del usuario y `act: orders-service`.
3. **C. Client credentials**: no hay usuario.
4. El token del usuario habría caducado y no debe viajar en el mensaje. El evento lleva la identidad **como dato**
   (`authid`/`sub`, *tenant*), `orders-service` se autentica ante Kafka con su identidad y las ACL garantizan que
   solo él publica en ese *topic*. `billing` registra el `sub` del evento y, si llama a otros servicios, lo hace con
   **client credentials**.
5. Es la alternativa **E**: cualquiera que llegue a la red interna (un servicio comprometido, un pod mal
   configurado) puede enviar `X-User-Id: admin`. Como mínimo: la gateway debe **borrar** esas cabeceras si llegan
   de fuera, la red debe impedir llamar a los servicios salvo desde la gateway (mTLS / *service mesh*) y, mejor,
   firmarlas (token interno, alternativa D) o reenviar el JWT para que cada servicio lo valide.
</details>

### A2. (Opcional) Proteger el proyecto con JWT

Añade Spring Security al proyecto como Resource Server JWT con estas reglas: `GET /api/products/**` público;
escribir en el catálogo requiere el *scope* `catalog.write`; `/api/orders/**` y `/api/bff/**` requieren el
*scope* `orders`; el *preflight* CORS siempre permitido. Pruébalo con `mockJwt()` **sin** IdP.

> Al activar la seguridad, los tests existentes que hacen `POST`/`PUT`/`DELETE` sin token pasarán a recibir 401:
> ejecuta solo el test nuevo (`./mvnw test -Dtest=SecurityConfigTest`).

<details><summary>Solución</summary>

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

```java
// security/SecurityConfig.java
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain api(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/actuator/health", "/index.html").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .pathMatchers("/api/products/**").hasAuthority("SCOPE_catalog.write")
                        .pathMatchers("/api/orders/**", "/api/bff/**").hasAuthority("SCOPE_orders")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .build();
    }
}
```

```java
// src/test/.../security/SecurityConfigTest.java
// jwk-set-uri: Boot necesita saber de dónde sacar las claves, pero con mockJwt() nunca se descargan
@SpringBootTest(properties = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9/jwks")
@AutoConfigureWebTestClient
class SecurityConfigTest {

    @Autowired
    WebTestClient client;

    @Test
    void rules() {
        client.get().uri("/api/products/1").exchange().expectStatus().isOk();
        client.get().uri("/api/orders").exchange().expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer");
        client.mutateWith(mockJwt()).get().uri("/api/orders").exchange().expectStatus().isForbidden();
        client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("SCOPE_orders")))
                .get().uri("/api/orders").exchange().expectStatus().isOk();
        client.mutateWith(mockJwt().jwt(jwt -> jwt.claim("scope", "orders catalog.write")))
                .delete().uri("/api/products/5").exchange().expectStatus().isNoContent();
    }
}
```

Observa además las cabeceras de la respuesta de `GET /api/products` (`Cache-Control: no-cache, no-store...`,
`X-Frame-Options: DENY`...) y compáralas con las de `GET /api/products/1`, que conserva su `Cache-Control: no-cache`
y su `ETag`.

Siguiente paso (reflexión): con la seguridad activada, ¿qué respondería el BFF al llamarse a sí mismo? Las
llamadas salientes no llevan token: pedidos respondería 401 y el BFF devolvería **502**. Habría que añadir
`ServerBearerExchangeFilterFunction` (*relay*) o un cliente OAuth2 (sección 2.4).
</details>

## Parte B — WebClient (25 min, tras el bloque 3)

### B1. Proxy en *streaming* sin materializar

Crea `GET /api/bff/products?maxPrice=60` (NDJSON) que pida **todo** el catálogo al microservicio de catálogo en
NDJSON y devuelva solo los productos con precio `≤ maxPrice`, **sin** `collectList()`: cada producto debe salir
hacia el cliente en cuanto llega del catálogo.

<details><summary>Solución</summary>

```java
// client/CatalogClient.java
public Flux<ProductDto> streamProducts() {
    return webClient.get().uri("/api/products")
            .accept(MediaType.APPLICATION_NDJSON)          // el catálogo responde en streaming
            .retrieve()
            .bodyToFlux(ProductDto.class)
            .timeout(Duration.ofSeconds(2));               // en un Flux: tiempo máximo ENTRE elementos
}

// bff/ProductBffController.java
@RestController
@RequestMapping("/api/bff/products")
public class ProductBffController {

    private final CatalogClient catalog;

    public ProductBffController(CatalogClient catalog) {
        this.catalog = catalog;
    }

    @GetMapping(produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ProductDto> cheaperThan(@RequestParam BigDecimal maxPrice) {
        return catalog.streamProducts()
                .filter(product -> product.price().compareTo(maxPrice) <= 0);
    }
}
```

```bash
curl -N localhost:8080/api/bff/products?maxPrice=60
# {"id":"2","name":"Ratón inalámbrico","price":29.95,"stock":100}
# {"id":"5","name":"Auriculares","price":59.00,"stock":40}
```

Con `Accept: application/json` en la llamada al catálogo, `bodyToFlux` también funcionaría, pero el catálogo
tendría que generar el array completo antes de enviarlo.
</details>

### B2. El catálogo como HTTP Service Client

Sustituye la llamada `webClient.get()...` de `CatalogClient.findProduct` por una interfaz `CatalogApi`
(`@HttpExchange`) registrada con `@ImportHttpServices` en un grupo `catalog`. Los tests del BFF deben seguir en
verde **sin tocarlos**. ¿Dónde quedan ahora el 404 → vacío, el *timeout* y los reintentos?

<details><summary>Solución</summary>

```java
// client/CatalogApi.java
@HttpExchange("/api/products")
public interface CatalogApi {

    @GetExchange("/{id}")
    Mono<ProductDto> findById(@PathVariable String id);
}

// client/ClientConfig.java: @ImportHttpServices es repetible
@ImportHttpServices(group = "orders", types = OrdersApi.class, clientType = HttpServiceGroup.ClientType.WEB_CLIENT)
@ImportHttpServices(group = "catalog", types = CatalogApi.class, clientType = HttpServiceGroup.ClientType.WEB_CLIENT)
public class ClientConfig { ... }

// client/CatalogClient.java: la POLÍTICA de la llamada sigue aquí; el cómo se hace la petición, en la interfaz
public Mono<ProductDto> findProduct(String id) {
    return api.findById(id)
            .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty())
            .timeout(TIMEOUT)
            .retryWhen(RETRY);
}
```

```properties
# Reutiliza la propiedad existente: así el @DynamicPropertySource de los tests sigue funcionando
spring.http.serviceclient.catalog.base-url=${app.services.catalog.base-url}
```

El 404 → vacío, el *timeout* y los reintentos **no** caben en la interfaz: siguen en `CatalogClient` (o en el
servicio). Los 7 tests de `OrderSummaryControllerTest` pasan, incluido el de propagación del `X-Request-Id`: el
`WebClientCustomizer` también se aplica al grupo `catalog`.
</details>

### B3. Rompe el paralelismo a propósito

En `OrderSummaryService.summary`, cambia `flatMapSequential(this::enrich, CONCURRENCY)` por
`concatMap(this::enrich)` y ejecuta `./mvnw test -Dtest=OrderSummaryControllerTest`. ¿Qué test falla y por qué?
¿Cambia el **resultado** del resumen? Deja el código como estaba.

<details><summary>Respuesta</summary>

Falla `callsTheCatalogInParallel`: el resumen tarda ~0,94 s (3 productos × 300 ms, uno detrás de otro) y el test
exige menos de 0,9 s. El resultado es **idéntico** (mismo orden, mismos datos): `concatMap` también conserva el
orden, pero no se suscribe a la llamada siguiente hasta que termina la anterior. `flatMapSequential` se suscribe a
todas a la vez y **reordena** los resultados; `flatMap` se suscribe a todas y entrega en orden de llegada.
</details>

## Parte C — Caché HTTP (15 min, tras el bloque 4)

### C1. ETag en el controlador anotado de pedidos

`GET /api/annotated/orders/{id}` debe comportarse como la versión funcional: `ETag`, `Last-Modified`,
`Cache-Control: max-age=3600, private` y 304 con `If-None-Match`.

<details><summary>Solución</summary>

```java
// orders/OrderController.java
@GetMapping("/{id}")
public Mono<ResponseEntity<Order>> findById(@PathVariable String id) {
    return service.findById(id)                                   // 404 -> OrderNotFoundException
            .map(order -> ResponseEntity.ok()
                    .eTag(order.id())
                    .lastModified(order.createdAt())
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                    .body(order));
}
```

El 304 lo genera `ResponseEntityResultHandler`: no hay que comparar nada a mano.
</details>

### C2. Concurrencia optimista con `If-Match`

`PUT /api/products/{id}` debe aceptar opcionalmente `If-Match: "<etag>"`. Si el producto ha cambiado desde que el
cliente lo leyó (el ETag no coincide), responde **412 Precondition Failed** como `ProblemDetail`. Si coincide,
actualiza y devuelve el **nuevo** ETag. Sin `If-Match`, funciona como antes.

<details><summary>Solución</summary>

```java
// catalog/ProductController.java
@PutMapping("/{id}")
public Mono<ResponseEntity<Product>> update(@PathVariable String id, @Valid @RequestBody ProductRequest request,
                                            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
    return service.findById(id)                                                     // 404 si no existe
            .filter(current -> ifMatch == null || ifMatch.equals("\"" + etag(current) + "\""))
            .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                    "El producto ha cambiado desde que lo leíste")))
            .flatMap(current -> service.update(id, request))
            .map(saved -> ResponseEntity.ok().eTag(etag(saved)).body(saved));      // nuevo ETag
}
```

```json
PUT /api/products/4 con If-Match: "viejo"  → 412
{"detail":"El producto ha cambiado desde que lo leíste","instance":"/api/products/4","status":412,
 "title":"Precondition Failed"}
```

Verificado: 412 con un ETag incorrecto; 200 y ETag nuevo con el correcto; 412 al repetir con el ETag **anterior**;
200 sin `If-Match`.

> Entre la comprobación y el `update` otra petición podría modificar el producto. Con una base de datos se hace en
> una sola sentencia: `UPDATE product SET ... , version = version + 1 WHERE id = ? AND version = ?` (o
> `@Version` en Spring Data), y 0 filas actualizadas → 412.
</details>

## Parte D — HTTP/2 (10 min, tras el bloque 5)

### D1. Observa el protocolo

```bash
curl -s -o /dev/null -w "%{http_version}\n" --http2-prior-knowledge http://localhost:8080/api/products/count
./mvnw spring-boot:run -Dspring-boot.run.profiles=tls
curl -k -s -o /dev/null -w "%{http_version}\n" https://localhost:8443/api/products/count
```

Abre <https://localhost:8443> en Chrome y activa la columna *Protocol* en DevTools → *Network*.

### D2. WebClient h2c contra un servidor que no lo habla

Configura un `WebClient` con `HttpClient.create().protocol(HttpProtocol.H2C)` y úsalo contra el servidor simulado
de los tests (`FakeRemoteServices`, un `HttpServer` de Reactor Netty con la configuración por defecto). ¿Qué
ocurre? ¿Cómo lo arreglas sin tocar el servidor?

<details><summary>Respuesta</summary>

Con solo `H2C`, el cliente usa *prior knowledge*: envía directamente el prefacio de HTTP/2 (`PRI * HTTP/2.0`). El
servidor, que solo habla HTTP/1.1, no lo entiende y la llamada falla:

```text
WebClientRequestException: First received frame was not SETTINGS. Hex dump for first 5 bytes: 485454502f
```

(`485454502f` es `HTTP/` en ASCII: el servidor respondió en HTTP/1.1). Con
`protocol(HttpProtocol.H2C, HttpProtocol.HTTP11)` el cliente empieza en HTTP/1.1 con `Upgrade: h2c`; el servidor
ignora la propuesta y la llamada funciona en HTTP/1.1. Contra el servidor del día (con `server.http2.enabled=true`)
la primera variante funciona en HTTP/2 (es lo que comprueba `Http2Test`).

Recuerda que, al fijar tu propio `clientConnector`, pierdes los *timeouts* de `spring.http.clients.*`: hay que
volver a configurarlos en el `HttpClient`.
</details>

⬅️ [Volver al índice del día](README.md)
