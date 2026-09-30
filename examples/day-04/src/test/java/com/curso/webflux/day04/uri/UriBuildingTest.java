package com.curso.webflux.day04.uri;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.DefaultUriBuilderFactory.EncodingMode;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * URI Links: construcción y codificación de URIs. Un concepto por test.
 * Referencia: https://docs.spring.io/spring-framework/reference/web/webflux/uri-building.html
 */
class UriBuildingTest {

    @Test
    void uriComponentsBuilderWithTemplateVariables() {
        UriComponents uri = UriComponentsBuilder
                .fromUriString("https://tienda.example/api/products/{id}")
                .queryParam("fields", "{fields}")
                .encode()                                   // codifica la plantilla y luego las variables
                .buildAndExpand("42", "name,price");

        // Las VARIABLES se codifican de forma estricta: hasta la "," pasa a %2C
        assertThat(uri.toUriString()).isEqualTo("https://tienda.example/api/products/42?fields=name%2Cprice");
        assertThat(uri.getPathSegments()).containsExactly("api", "products", "42");
    }

    @Test
    void buildWithMapOfVariables() {
        URI uri = UriComponentsBuilder.fromPath("/api/orders/{orderId}/details/{line}")
                .build(Map.of("orderId", "abc", "line", 2));

        assertThat(uri).hasToString("/api/orders/abc/details/2");
    }

    /**
     * encode() ANTES de expandir: los caracteres reservados que vienen DENTRO de una variable se codifican.
     * Un "/" en el valor no crea un segmento nuevo, y un "&" no parte el parámetro. Es la opción recomendada.
     */
    @Test
    void encodeBeforeExpandingProtectsVariableValues() {
        String path = UriComponentsBuilder.fromPath("/api/files/{name}")
                .encode()
                .buildAndExpand("informes/2026 v1")
                .toUriString();
        assertThat(path).isEqualTo("/api/files/informes%2F2026%20v1");

        URI query = UriComponentsBuilder.fromPath("/api/products/search")
                .queryParam("category", "{c}")
                .encode()
                .buildAndExpand("audio & vídeo")
                .toUri();
        assertThat(query.getRawQuery()).isEqualTo("category=audio%20%26%20v%C3%ADdeo");
    }

    /**
     * build().encode() DESPUÉS de expandir: solo se codifica lo que es ILEGAL en cada componente.
     * "/" es legal en una ruta, así que se queda tal cual y el valor se convierte en DOS segmentos.
     */
    @Test
    void encodeAfterExpandingOnlyEncodesIllegalChars() {
        String path = UriComponentsBuilder.fromPath("/api/files/{name}")
                .buildAndExpand("informes/2026 v1")
                .encode()
                .toUriString();

        assertThat(path).isEqualTo("/api/files/informes/2026%20v1");
    }

    /** UriBuilderFactory: plantilla base compartida (la usa WebClient el día 3). */
    @Test
    void uriBuilderFactoryWithBaseUrl() {
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory("http://localhost:8080/api");
        factory.setEncodingMode(EncodingMode.TEMPLATE_AND_VALUES);   // el modo por defecto

        URI uri = factory.uriString("/products/{id}").queryParam("v", "{v}").build("1", "a b");

        assertThat(uri).hasToString("http://localhost:8080/api/products/1?v=a%20b");
    }

    @Test
    void uriUtilsEncodesSingleValues() {
        assertThat(UriUtils.encodePathSegment("a/b c", "UTF-8")).isEqualTo("a%2Fb%20c");
        assertThat(UriUtils.decode("a%2Fb%20c", "UTF-8")).isEqualTo("a/b c");
    }
}
