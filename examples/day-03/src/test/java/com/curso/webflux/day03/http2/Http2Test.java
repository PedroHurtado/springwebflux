package com.curso.webflux.day03.http2;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.netty.handler.codec.http.HttpVersion;
import reactor.core.publisher.Mono;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.test.StepVerifier;

/**
 * server.http2.enabled=true sin TLS: el servidor habla h2c (HTTP/2 en claro) Y sigue hablando HTTP/1.1.
 * Se usa el HttpClient de Reactor Netty (el que hay debajo de WebClient) porque expone la versión
 * del protocolo con la que se ha respondido.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Http2Test {

    @LocalServerPort
    int port;

    @Test
    void speaksH2cWithPriorKnowledge() {
        HttpClient h2c = HttpClient.create().protocol(HttpProtocol.H2C);   // como curl --http2-prior-knowledge

        StepVerifier.create(versionOf(h2c))
                .expectNext(HttpVersion.valueOf("HTTP/2.0"))
                .verifyComplete();
    }

    @Test
    void stillSpeaksHttp11() {
        StepVerifier.create(versionOf(HttpClient.create()))              // por defecto: HTTP/1.1
                .expectNext(HttpVersion.HTTP_1_1)
                .verifyComplete();
    }

    private Mono<HttpVersion> versionOf(HttpClient client) {
        return client.get()
                .uri("http://localhost:" + port + "/api/products/count")
                .responseSingle((response, body) -> body.then(Mono.just(response.version())));
    }
}
