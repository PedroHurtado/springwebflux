package com.curso.webflux.day04.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.service.registry.HttpServiceGroup;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Clientes HTTP de los "otros microservicios".
 *
 * En el curso, catálogo y pedidos viven en esta misma aplicación, pero el BFF los llama por HTTP como
 * si estuvieran en otras máquinas: basta con cambiar las URLs en application.properties.
 *
 * - Pedidos: HTTP Service Client (@ImportHttpServices, Spring Framework 7 + Spring Boot 4). Boot crea el
 *   proxy de OrdersApi y lo configura con spring.http.serviceclient.orders.*.
 *   ⚠️ clientType = WEB_CLIENT: sin él se usaría RestClient, que es bloqueante.
 * - Catálogo: un WebClient construido a partir del WebClient.Builder de Spring Boot. Usar el builder
 *   (y no WebClient.create()) aporta los codecs de Jackson configurados, las métricas y trazas, los
 *   timeouts de spring.http.clients.* y los WebClientCustomizer (CorrelationIdPropagation).
 */
@Configuration
@ImportHttpServices(group = "orders", types = OrdersApi.class, clientType = HttpServiceGroup.ClientType.WEB_CLIENT)
public class ClientConfig {

    /**
     * El WebClient.Builder autoconfigurado es un bean prototype: cada inyección recibe una copia
     * nueva, así que fijar aquí la baseUrl no afecta a otros clientes.
     */
    @Bean
    WebClient catalogWebClient(WebClient.Builder builder, @Value("${app.services.catalog.base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
