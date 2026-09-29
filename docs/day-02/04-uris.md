# 4. URI's

> Temario: **URI's** · Duración: 20 min
> Referencia oficial: [Spring WebFlux — URI Links](https://docs.spring.io/spring-framework/reference/web/webflux/uri-building.html)
>
> Código: `catalog/ProductController.create`, `orders/OrderHandler.create` · Tests: `uri/UriBuildingTest.java`

Construir URIs concatenando texto es fuente de errores: barras duplicadas, parámetros sin codificar, un `&` en un
valor que "parte" la query... Spring ofrece una API para construirlas y codificarlas bien. Es la misma en MVC,
en WebFlux y en `WebClient` (día 3).

## 4.1 `UriComponentsBuilder`

```java
UriComponents uri = UriComponentsBuilder
        .fromUriString("https://tienda.example/api/products/{id}")
        .queryParam("fields", "{fields}")
        .encode()                                 // 1) codifica la plantilla
        .buildAndExpand("42", "name,price");      // 2) sustituye y codifica las variables

uri.toUriString();   // https://tienda.example/api/products/42?fields=name%2Cprice
uri.toUri();         // java.net.URI
```

Variantes útiles:

```java
UriComponentsBuilder.fromPath("/api/orders/{orderId}/details/{line}")
        .build(Map.of("orderId", "abc", "line", 2));             // URI: /api/orders/abc/details/2

UriComponentsBuilder.fromUriString("http://localhost:8080")
        .path("/api/products").queryParam("category", "audio")
        .build().toUriString();                                  // sin variables
```

## 4.2 Codificación: `encode()` antes o después

| Forma | Qué codifica | Con el valor `"informes/2026 v1"` en `/api/files/{name}` |
|---|---|---|
| `.encode().buildAndExpand(vars)` ✅ | La plantilla, y **todos** los caracteres reservados de las variables | `/api/files/informes%2F2026%20v1` (un segmento) |
| `.buildAndExpand(vars).encode()` | Solo lo que es **ilegal** en cada componente | `/api/files/informes/2026%20v1` (¡dos segmentos!) |

La primera es la recomendada: los valores de las variables son **datos**, y un `/`, `&`, `=` o `,` dentro de
ellos no debe cambiar la estructura de la URI.

```java
// Con la query: "&" dentro del valor se codifica (si no, crearía otro parámetro)
UriComponentsBuilder.fromPath("/api/products/search")
        .queryParam("category", "{c}")
        .encode()
        .buildAndExpand("audio & vídeo")
        .toUri()
        .getRawQuery();          // category=audio%20%26%20v%C3%ADdeo
```

Para codificar un valor suelto: `UriUtils.encodePathSegment("a/b c", UTF_8)` → `a%2Fb%20c`.

📄 `src/test/.../uri/UriBuildingTest.java`: cada caso de esta sección es un test ejecutable.

## 4.3 `UriBuilderFactory`

Cuando muchas URIs comparten una base (típico de un cliente HTTP), se usa una **fábrica** configurada una vez:

```java
DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory("http://localhost:8080/api");
factory.setEncodingMode(EncodingMode.TEMPLATE_AND_VALUES);     // el modo por defecto

URI uri = factory.uriString("/products/{id}").queryParam("v", "{v}").build("1", "a b");
// http://localhost:8080/api/products/1?v=a%20b
```

| `EncodingMode` | Equivale a |
|---|---|
| `TEMPLATE_AND_VALUES` (defecto) | `.encode().buildAndExpand(...)`: plantilla + variables estrictas |
| `VALUES_ONLY` | Solo las variables (la plantilla se considera ya codificada) |
| `URI_COMPONENT` | `.buildAndExpand(...).encode()`: solo lo ilegal por componente |
| `NONE` | Ninguna codificación |

`WebClient.builder().baseUrl(...)` crea internamente un `DefaultUriBuilderFactory`: lo retomaremos el día 3.

## 4.4 URIs relativas a la petición actual

La cabecera `Location` de un 201 debe ser una URI **absoluta** (o al menos correcta para el cliente). En lugar
de escribir `"http://localhost:8080"` a mano, se parte de la petición que se está atendiendo:

```java
// Controlador anotado: UriComponentsBuilder como argumento (viene con esquema, host y puerto de la petición)
@PostMapping
public Mono<ResponseEntity<Product>> create(@Valid @RequestBody Mono<ProductRequest> request,
                                            UriComponentsBuilder uriBuilder) {
    return request.flatMap(service::create)
            .map(p -> ResponseEntity
                    .created(uriBuilder.path("/api/products/{id}").buildAndExpand(p.id()).toUri())
                    .body(p));
}

// Endpoint funcional: ServerRequest.uriBuilder() parte de la URI COMPLETA de la petición (/api/orders)
ServerResponse.created(request.uriBuilder().path("/{id}").build(order.id()))
```

```bash
curl -i -X POST http://localhost:8080/api/products -H "Content-Type: application/json" \
     -d '{"name":"Webcam","category":"perifericos","price":39.9,"stock":12}'
# Location: http://localhost:8080/api/products/205753c4-...
```

### Detrás de un proxy: cabeceras `Forwarded`

Si la aplicación está detrás de un balanceador o *proxy* inverso (`https://tienda.example` → `http://10.0.0.5:8080`),
el host y el esquema "reales" llegan en las cabeceras `Forwarded` o `X-Forwarded-Host`/`-Proto`/`-Port`.
WebFlux las aplica con `ForwardedHeaderTransformer`, que en Spring Boot se activa con:

```properties
server.forward-headers-strategy=framework
```

> ⚠️ Solo debe activarse si **siempre** hay un *proxy* de confianza delante: un cliente podría falsificar esas
> cabeceras.

## 4.5 ¿Y los enlaces a controladores?

Spring MVC tiene `MvcUriComponentsBuilder.fromMethodOn(...)` para generar la URI de un método de controlador.
**WebFlux no tiene equivalente**: se construyen con `UriComponentsBuilder` como en 4.4 (o con Spring HATEOAS,
que sí soporta WebFlux, si se quieren enlaces hipermedia).

## Referencias para ampliar

- URI Links (WebFlux): <https://docs.spring.io/spring-framework/reference/web/webflux/uri-building.html>
- `UriComponentsBuilder` (Javadoc): <https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/util/UriComponentsBuilder.html>
- `DefaultUriBuilderFactory` (Javadoc): <https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/util/DefaultUriBuilderFactory.html>
- Forwarded Headers (WebFlux): <https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html#webflux-forwarded-headers>
- Spring Boot — Running behind a front-end proxy server: <https://docs.spring.io/spring-boot/how-to/webserver.html#howto.webserver.use-behind-a-proxy-server>
- RFC 3986 — URI Generic Syntax: <https://www.rfc-editor.org/rfc/rfc3986.html>
- RFC 7239 — Forwarded HTTP Extension: <https://www.rfc-editor.org/rfc/rfc7239.html>

➡️ Siguiente: [5. CORS](05-cors.md)
