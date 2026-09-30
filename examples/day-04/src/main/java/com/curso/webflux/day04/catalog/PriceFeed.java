package com.curso.webflux.day04.catalog;

import org.springframework.stereotype.Component;

import com.curso.webflux.day04.catalog.ProductService.PriceChange;

import reactor.core.publisher.Flux;

/**
 * Día 4: UN solo ticker de precios compartido por todos los clientes (SSE y WebSocket).
 *
 * priceTicker() es FRÍO: con 100 navegadores abiertos habría 100 tickers cambiando precios.
 * share() lo convierte en CALIENTE (= publish().refCount(1)):
 *   - el primer suscriptor arranca el ticker;
 *   - los siguientes se enganchan al mismo flujo y reciben los cambios a partir de ese momento;
 *   - cuando se va el último, se cancela (y el siguiente que llegue lo vuelve a arrancar).
 */
@Component
public class PriceFeed {

    private final Flux<PriceChange> changes;

    public PriceFeed(ProductService service) {
        this.changes = service.priceTicker().share();
    }

    public Flux<PriceChange> changes() {
        return changes;
    }
}
