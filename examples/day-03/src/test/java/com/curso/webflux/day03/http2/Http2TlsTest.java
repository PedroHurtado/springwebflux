package com.curso.webflux.day03.http2;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.Http2SslContextSpec;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.SslProvider.GenericSslContextSpec;
import reactor.test.StepVerifier;

/**
 * Perfil "tls" (application-tls.properties): HTTPS con un certificado autofirmado. Cliente y servidor
 * acuerdan h2 durante el handshake TLS (ALPN), sin petición de Upgrade.
 *
 * InsecureTrustManagerFactory acepta cualquier certificado: SOLO para probar con el autofirmado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("tls")
class Http2TlsTest {

    @LocalServerPort
    int port;

    @Test
    void negotiatesH2OverTls() {
        // Tipo GenericSslContextSpec<?> a propósito: Http2SslContextSpec también es ProtocolSslContextSpec y,
        // sin él, Java elegiría la sobrecarga sslContext(ProtocolSslContextSpec), obsoleta en Reactor Netty 1.3
        GenericSslContextSpec<?> tls = Http2SslContextSpec.forClient()
                .configure(builder -> builder.trustManager(InsecureTrustManagerFactory.INSTANCE));
        HttpClient h2 = HttpClient.create()
                .protocol(HttpProtocol.H2)
                .secure(ssl -> ssl.sslContext(tls));

        StepVerifier.create(h2.get()
                        .uri("https://localhost:" + port + "/api/products/count")
                        .responseSingle((response, body) -> body.then(Mono.just(response.version()))))
                .expectNext(HttpVersion.valueOf("HTTP/2.0"))
                .verifyComplete();
    }
}
