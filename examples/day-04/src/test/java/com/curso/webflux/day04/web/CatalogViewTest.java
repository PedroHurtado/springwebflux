package com.curso.webflux.day04.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.util.function.Tuple2;

/**
 * Vistas Thymeleaf con WebTestClient: se comprueba el HTML generado (estado, content-type y contenido).
 * Los precios del catálogo inicial no se comprueban: el ticker (test de SSE) los cambia en el contexto compartido.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class CatalogViewTest {

    @Autowired
    WebTestClient client;

    @Test
    void listResolvesTheFluxAndRendersEveryProduct() {
        client.get().uri("/catalog?category=perifericos")
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(html -> assertThat(html)
                        .contains("Teclado mecánico", "Ratón inalámbrico", " €</td>")
                        .doesNotContain("Monitor 27 pulgadas"));
    }

    /**
     * Modo data-driven: la respuesta llega POR TROZOS. Se mide cuándo llega cada línea del HTML:
     * la cabecera de la página llega enseguida y la última fila, tras 5 productos x 400 ms.
     * recordWith/consumeRecordedWith: StepVerifier guarda los elementos para comprobarlos juntos al final.
     */
    @Test
    void liveRendersTheTableInChunksAsProductsArrive() {
        Flux<String> lines = client.get().uri("/catalog/live")
                .accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody();                        // StringDecoder: una línea del HTML por elemento

        StepVerifier.create(lines.elapsed())               // (ms desde el elemento anterior, línea)
                .recordWith(ArrayList::new)
                .thenConsumeWhile(line -> true)
                .consumeRecordedWith(recorded -> {
                    List<Tuple2<Long, String>> timed = List.copyOf(recorded);
                    long first = timed.get(0).getT1();
                    long total = timed.stream().mapToLong(Tuple2::getT1).sum();
                    assertThat(first).as("la cabecera no espera a los datos").isLessThan(1_000);
                    assertThat(total).as("las filas llegan según se emiten").isGreaterThanOrEqualTo(1_800);
                    assertThat(timed).extracting(Tuple2::getT2).anyMatch(line -> line.contains("Auriculares"));
                })
                .expectComplete()
                .verify(Duration.ofSeconds(10));
    }

    @Test
    void detailUsesRenderingAndUnknownProductIsAn404Page() {
        client.get().uri("/catalog/4")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Portátil 14 pulgadas", "Precio con IVA"));

        client.get().uri("/catalog/no-existe")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)   // una página, no ProblemDetail
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Producto no encontrado", "no-existe"));
    }

    @Test
    void invalidFormIsRenderedAgainWithErrorsAndTheUserInput() {
        client.post().uri("/catalog")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("name", "Webcam HD")
                        .with("category", "")
                        .with("price", "-3")
                        .with("stock", "7"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html)
                        .contains("la categoría es obligatoria", "el precio debe ser mayor que 0")
                        .contains("value=\"Webcam HD\""));                 // th:field conserva lo escrito
    }

    @Test
    void validFormRedirectsToTheNewProduct() {
        String location = client.post().uri("/catalog")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("name", "Webcam HD")
                        .with("category", "video")
                        .with("price", "45.50")
                        .with("stock", "7"))
                .exchange()
                .expectStatus().isSeeOther()                              // 303: Post/Redirect/Get
                .expectHeader().valueMatches("Location", "/catalog/.+")
                .returnResult(Void.class)
                .getResponseHeaders().getFirst("Location");

        client.get().uri(location)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(html -> assertThat(html).contains("Webcam HD", "45,50 €"));
    }
}
