# 6. Configuración de WebFlux

> Temario: **Configuración de WebFlux** · Duración: 30 min
> Referencias oficiales: [Spring WebFlux — WebFlux Config](https://docs.spring.io/spring-framework/reference/web/webflux/config.html) ·
> [API Versioning](https://docs.spring.io/spring-framework/reference/web/webflux-versioning.html)
>
> Código: `config/WebConfig.java`, `application.properties`, `ProductController.findById` / `findByIdV2`

## 6.1 `@EnableWebFlux` o Spring Boot

| | Spring Framework "a pelo" | Spring Boot |
|---|---|---|
| Activar WebFlux | `@EnableWebFlux` en una clase `@Configuration` | Automático con `spring-boot-starter-webflux` |
| Personalizar | Implementar `WebFluxConfigurer` | Implementar `WebFluxConfigurer` **sin** `@EnableWebFlux` + propiedades `spring.webflux.*` |
| Control total | Extender `DelegatingWebFluxConfiguration` | Declarar `@EnableWebFlux` (se pierde la autoconfiguración) |

> ⚠️ En Spring Boot, `@EnableWebFlux` **desactiva** la autoconfiguración de WebFlux: codecs de Boot, propiedades
> `spring.webflux.*` y `spring.http.codecs.*`, recursos estáticos, conversores... Casi nunca es lo que quieres.

```java
@Configuration                                   // sin @EnableWebFlux
public class WebConfig implements WebFluxConfigurer {
    // se sobrescriben solo los métodos necesarios (todos son "default")
}
```

## 6.2 Qué se puede configurar

Métodos de `WebFluxConfigurer` en Spring Framework 7:

| Método | Para qué | En el curso |
|---|---|---|
| `addFormatters` | Conversores y formateadores de tipos | ✅ `StringToProductSortConverter` (bloque 1) |
| `getValidator` | Sustituir el `Validator` global | Boot ya configura Bean Validation |
| `addCorsMappings` | CORS global | ✅ bloque 5 |
| `configureApiVersioning` | Versionado de API (**nuevo en Spring 7**) | ✅ 6.6 |
| `configureHttpMessageCodecs` | Codecs: límites de memoria, codecs propios, *logging* | 6.3 |
| `configurePathMatching` | Prefijos de ruta, sensibilidad a mayúsculas | 6.5 |
| `addResourceHandlers` | Recursos estáticos y su caché | 6.4 |
| `configureContentTypeResolver` | Cómo se decide el tipo de respuesta (cabecera `Accept`, parámetro...) | — |
| `configureArgumentResolvers` | Argumentos de controlador propios | — |
| `configureBlockingExecution` | Ejecutar en un `Executor` (p. ej. hilos virtuales) los métodos de controlador que no devuelven tipos reactivos | — |
| `addErrorResponseInterceptors` | Modificar todas las respuestas `ErrorResponse` antes de escribirlas | — |
| `configureViewResolvers` | Motores de plantillas | Día 4 |
| `getWebSocketService` | Servicio WebSocket | Día 4 |

## 6.3 Codecs HTTP

Los *codecs* convierten entre bytes y objetos (JSON, NDJSON, SSE, texto, multipart...). Por seguridad, cuando
un codec necesita **acumular** el cuerpo en memoria (p. ej. para decodificar un JSON completo) lo limita a
**256 KB** por defecto; si se supera, falla con `DataBufferLimitException`.

```properties
# Con Spring Boot (recomendado)
spring.http.codecs.max-in-memory-size=512KB
spring.http.codecs.log-request-details=false     # true: el log DEBUG incluye cabeceras y parámetros (¡datos sensibles!)
```

```java
// Equivalente en código
@Override
public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
    configurer.defaultCodecs().maxInMemorySize(512 * 1024);
}
```

En Boot también se puede declarar un bean `CodecCustomizer` para ajustar los codecs sin implementar el configurador.

## 6.4 Recursos estáticos

Spring Boot sirve lo que haya en `classpath:/static/` (así se sirven `index.html` y `cors.html` en el ejemplo).
Propiedades: `spring.web.resources.static-locations`, `spring.web.resources.cache.period`,
`spring.webflux.static-path-pattern`. Para casos más finos:

```java
@Override
public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/docs/**")
            .addResourceLocations("classpath:/public-docs/")
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)));   // caché HTTP: día 3
}
```

## 6.5 *Path matching*

```java
@Override
public void configurePathMatching(PathMatchConfigurer configurer) {
    // Todos los @RestController cuelgan de /api sin repetirlo en cada @RequestMapping
    configurer.addPathPrefix("/api", HandlerTypePredicate.forAnnotation(RestController.class));
}
```

- Desde Spring Framework 6, `/api/products/` (con barra final) **no** coincide con `/api/products`. Si hay que
  tolerarlo, se usa el filtro `UrlHandlerFilter` (redirige o reescribe la ruta).
- `spring.webflux.base-path=/tienda` añade un prefijo a **toda** la aplicación (útil detrás de un *proxy*).

## 6.6 *API versioning* (novedad de Spring Framework 7)

Cuando una API pública evoluciona sin romper a los clientes existentes, conviven varias **versiones** del mismo
recurso. Spring Framework 7 lo soporta de serie.

### 1. Estrategia: dónde viaja la versión

```java
// config/WebConfig.java
@Override
public void configureApiVersioning(ApiVersionConfigurer configurer) {
    configurer.useRequestHeader("API-Version")        // dónde se lee la versión
            .addSupportedVersions("1.0", "2.0")       // versiones válidas
            .setDefaultVersion("1.0");                // si la petición no indica ninguna
}
```

| Estrategia | Método | Ejemplo de petición |
|---|---|---|
| Cabecera | `useRequestHeader("API-Version")` | `API-Version: 2.0` |
| Parámetro de consulta | `useQueryParam("version")` | `/api/products/4?version=2.0` |
| Segmento de ruta | `usePathSegment(1)` | `/api/2.0/products/4` (el segmento 1 contiene la versión) |
| Parámetro del *media type* | `useMediaTypeParameter(MediaType.APPLICATION_JSON, "v")` | `Accept: application/json;v=2.0` |

Con Spring Boot se puede hacer lo mismo con propiedades:

```properties
spring.webflux.apiversion.use.header=API-Version
spring.webflux.apiversion.supported=1.0,2.0
spring.webflux.apiversion.default=1.0
```

### 2. Mapear cada versión

```java
@GetMapping(path = "/{id}", version = "1.0")
public Mono<Product> findById(@PathVariable String id) { ... }

@GetMapping(path = "/{id}", version = "2.0")
public Mono<ProductV2> findByIdV2(@PathVariable String id) {
    return service.findById(id).map(ProductV2::from);
}
```

- Las versiones se interpretan como *semantic versioning*: `"2"`, `"2.0"` y `"2.0.0"` son la misma.
- `version = "1.1+"` (versión **base**) significa "la 1.1 y las siguientes soportadas": evita duplicar
  endpoints que no cambian en cada versión. Si varias coinciden, gana la más alta que no supere la pedida.
- Los métodos **sin** `version` responden a cualquier versión, salvo que otro método coincida de forma más
  específica (en el ejemplo, todos los endpoints salvo `findById` / `findByIdV2`).
- En endpoints funcionales: `GET("/{id}", version("2.0"), handler::findByIdV2)`.

### 3. Qué pasa con cada petición

```bash
curl http://localhost:8080/api/products/4                           # sin cabecera -> 1.0
# {"id":"4","name":"Portátil 14 pulgadas","category":"portatiles","price":1099.00,"stock":5}

curl -H "API-Version: 2.0" http://localhost:8080/api/products/4
# {"id":"4","name":"Portátil 14 pulgadas","price":1099.00,"priceWithVat":1329.79,"available":true,
#  "category":"portatiles","warning":"Quedan 5 unidades"}

curl -i -H "API-Version: 3.0" http://localhost:8080/api/products/4
# HTTP/1.1 400 Bad Request   (application/problem+json)
# {"detail":"Invalid API version: '3.0.0'.", ...}
```

| Situación | Resultado |
|---|---|
| Versión no soportada | 400 (`InvalidApiVersionException`) |
| Sin versión y sin `defaultVersion` (por defecto la versión es **obligatoria**) | 400 (`MissingApiVersionException`) |
| Sin versión con `setVersionRequired(false)` | Se usa la versión más reciente |
| Sin versión con `setDefaultVersion("1.0")` (el ejemplo) | Se usa la 1.0 |

Las versiones soportadas se detectan también de los `version = ...` declarados en los controladores
(`detectSupportedVersions`, activo por defecto).

### 4. Deprecar una versión

`setDeprecationHandler(...)` con un `StandardApiVersionDeprecationHandler` añade a las respuestas de una versión
obsoleta las cabeceras estándar `Deprecation`, `Sunset` y `Link` (RFC 9745 y RFC 8594), para que los clientes se
enteren de que deben migrar (ejercicio en el laboratorio).

El cliente (`WebClient`, día 3) también sabe enviar la versión: `ApiVersionInserter`.

## 6.7 Resumen de propiedades de Spring Boot usadas hoy

| Propiedad | Efecto |
|---|---|
| `spring.jackson.deserialization.fail-on-unknown-properties=true` | 400 si el JSON trae campos desconocidos |
| `spring.http.codecs.max-in-memory-size=512KB` | Límite de memoria de los codecs |
| `spring.webflux.multipart.max-in-memory-size=64KB` | Por encima, cada parte multipart va a disco |
| `spring.webflux.problemdetails.enabled=true` | `ProblemDetail` por defecto (sin `@ControllerAdvice` propio) |
| `spring.webflux.apiversion.*` | Versionado de API sin código |
| `spring.webflux.format.date` / `date-time` / `time` | Formato de fechas en parámetros |
| `spring.webflux.base-path` | Prefijo de toda la aplicación |
| `server.forward-headers-strategy=framework` | Respetar cabeceras `Forwarded` (bloque 4) |

## Referencias para ampliar

- WebFlux Config: <https://docs.spring.io/spring-framework/reference/web/webflux/config.html>
- API Versioning (WebFlux): <https://docs.spring.io/spring-framework/reference/web/webflux-versioning.html>
- Spring Boot — Reactive Web Applications: <https://docs.spring.io/spring-boot/reference/web/reactive.html>
- Spring Boot — Common Application Properties (web): <https://docs.spring.io/spring-boot/appendix/application-properties/index.html#appendix.application-properties.web>
- Codecs (límites y configuración): <https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html#webflux-codecs>
- RFC 9745 — The Deprecation HTTP Response Header Field: <https://www.rfc-editor.org/rfc/rfc9745.html>
- RFC 8594 — The Sunset HTTP Header Field: <https://www.rfc-editor.org/rfc/rfc8594.html>

➡️ Siguiente: [7. Laboratorio](07-laboratorio.md)
