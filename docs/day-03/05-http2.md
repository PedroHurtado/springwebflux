# 5. HTTP/2

> Temario: **HTTP/2** · Duración: 25 min
> Referencia oficial: [Spring WebFlux — HTTP/2](https://docs.spring.io/spring-framework/reference/web/webflux/http2.html) ·
> [Spring Boot — HTTP/2 with Reactor Netty](https://docs.spring.io/spring-boot/how-to/webserver.html#howto.webserver.configure-http2.netty)
>
> Código: `application.properties` (`server.http2.enabled`), `application-tls.properties` (perfil `tls`),
> `src/main/resources/tls/demo.p12` · Tests: `http2/Http2Test.java`, `http2/Http2TlsTest.java`

## 5.1 Qué aporta HTTP/2

HTTP/2 no cambia la **semántica** de HTTP (métodos, cabeceras, códigos de estado, caché: todo lo visto sigue
igual). Cambia **cómo viajan** los mensajes:

| | HTTP/1.1 | HTTP/2 |
|---|---|---|
| Formato | Texto | **Tramas binarias** |
| Peticiones por conexión | Una detrás de otra (el navegador abre ~6 conexiones por origen) | **Multiplexación**: muchas peticiones a la vez en **una** conexión (*streams*) |
| Cabeceras | Se repiten enteras en cada petición | Comprimidas con **HPACK** (solo se envían las diferencias) |
| Bloqueo en cabeza de línea (*head-of-line blocking*) | Una respuesta lenta retiene a las siguientes de esa conexión | Eliminado a nivel HTTP (persiste a nivel TCP; HTTP/3 lo resuelve con QUIC) |
| Prioridades, control de flujo por *stream* | No | Sí |

Por qué importa en una aplicación **WebFlux**:

- **SSE y *streaming***: con HTTP/1.1 cada `EventSource` ocupa una de las ~6 conexiones que el navegador permite
  por origen; con 6 pestañas abiertas en `/api/products/prices`, la séptima petición se queda esperando. Con
  HTTP/2 todas comparten una conexión.
- **Entre microservicios**: menos conexiones y menos *handshakes* TLS; muchas llamadas concurrentes (como las N
  del BFF al catálogo) multiplexadas en una sola conexión.

## 5.2 h2 y h2c

| Variante | Transporte | Cómo se acuerda | Quién la usa |
|---|---|---|---|
| **h2** | Sobre **TLS** | Durante el *handshake* TLS, con la extensión **ALPN** | Navegadores (**solo** usan HTTP/2 con TLS), APIs públicas |
| **h2c** | En claro (TCP) | *Prior knowledge* (el cliente empieza directamente en HTTP/2) o petición HTTP/1.1 con `Upgrade: h2c` | Tráfico interno (detrás de un balanceador o de un *service mesh* que termina TLS), gRPC |

## 5.3 HTTP/2 en Spring Boot con Reactor Netty

Una sola propiedad:

```properties
server.http2.enabled=true
```

- **Sin TLS** (`application.properties` del ejemplo): Reactor Netty acepta **h2c y HTTP/1.1** en el mismo puerto.
- **Con TLS** (perfil `tls`): **h2 y HTTP/1.1**, negociados por ALPN.

```properties
# application-tls.properties  ->  ./mvnw spring-boot:run -Dspring-boot.run.profiles=tls
server.port=8443
server.ssl.bundle=demo
spring.ssl.bundle.jks.demo.keystore.location=classpath:tls/demo.p12
spring.ssl.bundle.jks.demo.keystore.password=changeit
spring.ssl.bundle.jks.demo.keystore.type=PKCS12
```

El certificado del ejemplo es **autofirmado** y solo sirve para el curso. Se generó así:

```bash
keytool -genkeypair -alias demo -keyalg RSA -keysize 2048 -validity 3650 -storetype PKCS12 \
        -keystore src/main/resources/tls/demo.p12 -storepass changeit \
        -dname "CN=localhost" -ext "SAN=dns:localhost,ip:127.0.0.1"
```

- Los **SSL bundles** (`spring.ssl.bundle.*`) son la forma actual de configurar TLS en Spring Boot: el mismo
  *bundle* se puede usar en el servidor, en `WebClient` (`spring.http.clients.ssl.bundle`), en Kafka, etc. También
  aceptan certificados PEM (`spring.ssl.bundle.pem.*`) y se pueden recargar en caliente (`reload-on-update`).
- En producción el certificado lo emite una CA (Let's Encrypt, la CA interna) y la contraseña **nunca** va en el
  repositorio: variables de entorno o un gestor de secretos.
- En WebFlux **no hay que tocar código**: los controladores, SSE y NDJSON funcionan igual sobre HTTP/2.
- Con h2c y peticiones `Upgrade` que llevan cuerpo, Reactor Netty limita lo que acumula durante el *upgrade* con
  `server.netty.h2c-max-content-length` (por defecto `0B`: una petición con cuerpo no se "promociona" a HTTP/2 y
  se atiende en HTTP/1.1).

## 5.4 WebClient sobre HTTP/2

`WebClient` habla HTTP/1.1 por defecto. Para HTTP/2 se configura el `HttpClient` de Reactor Netty:

```java
// h2c (servicios internos sin TLS)
WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(HttpClient.create().protocol(HttpProtocol.H2C)))
        .build();

// h2 con TLS: HttpProtocol.H2 + .secure(...) con el certificado de confianza (o un SSL bundle de Boot)
HttpClient.create()
        .protocol(HttpProtocol.H2, HttpProtocol.HTTP11)      // ofrece ambos; ALPN elige
        .secure(ssl -> ssl.sslContext(Http2SslContextSpec.forClient()));
```

## 5.5 Probarlo

```bash
./mvnw spring-boot:run
curl -s -o /dev/null -w "%{http_version}\n" --http2-prior-knowledge http://localhost:8080/api/products/count   # 2
curl -s -o /dev/null -w "%{http_version}\n" http://localhost:8080/api/products/count                          # 1.1

./mvnw spring-boot:run -Dspring-boot.run.profiles=tls
curl -k -s -o /dev/null -w "%{http_version}\n" https://localhost:8443/api/products/count                       # 2 (ALPN)
```

En Chrome, abre <https://localhost:8443> (acepta el aviso del certificado autofirmado), DevTools → *Network* →
clic derecho en la cabecera de la tabla → activa la columna **Protocol**: verás `h2`. Con
<http://localhost:8080> verás `http/1.1`, porque los navegadores no usan h2c.

📄 Tests (usan el `HttpClient` de Reactor Netty porque expone la versión con la que respondió el servidor):

| Test | Qué comprueba |
|---|---|
| `Http2Test.speaksH2cWithPriorKnowledge` | `HttpProtocol.H2C` → la respuesta es `HTTP/2.0` |
| `Http2Test.stillSpeaksHttp11` | El mismo puerto sigue atendiendo HTTP/1.1 |
| `Http2TlsTest.negotiatesH2OverTls` | Perfil `tls`: h2 negociado por ALPN → `HTTP/2.0` |

> **HTTP/3** (sobre QUIC/UDP) resuelve el bloqueo en cabeza de línea a nivel de transporte. Reactor Netty incluye
> soporte de HTTP/3 (necesita las librerías nativas de QUIC de Netty); en la práctica suele terminarse en el
> balanceador o la CDN y hablar HTTP/2 o HTTP/1.1 hacia dentro.

## Referencias para ampliar

- Spring WebFlux — HTTP/2: <https://docs.spring.io/spring-framework/reference/web/webflux/http2.html>
- Spring Boot — Configure HTTP/2 (Reactor Netty): <https://docs.spring.io/spring-boot/how-to/webserver.html#howto.webserver.configure-http2.netty>
- Spring Boot — SSL bundles: <https://docs.spring.io/spring-boot/reference/features/ssl.html>
- Reactor Netty — HTTP Server (HTTP/2): <https://projectreactor.io/docs/netty/release/reference/http-server.html>
- RFC 9113 — HTTP/2: <https://www.rfc-editor.org/rfc/rfc9113>
- RFC 7541 — HPACK: <https://www.rfc-editor.org/rfc/rfc7541>
- MDN — Evolution of HTTP: <https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Evolution_of_HTTP>

➡️ Siguiente: [6. Laboratorio](06-laboratorio.md)
