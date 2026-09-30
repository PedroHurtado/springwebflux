package com.curso.webflux.day04.catalog;

import java.math.BigDecimal;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Modelo inmutable. Día 4: además es la ENTIDAD de Spring Data R2DBC (tabla product, schema.sql).
 *
 * - @Table / @Id: mapeo a la tabla. Tabla y columnas se deducen de los nombres (Product -> PRODUCT,
 *   category -> CATEGORY; un componente productName iría a PRODUCT_NAME).
 *   ⚠️ No se escribe @Table("product"): Spring Data pone los nombres ENTRE COMILLAS en el SQL y en H2 (y en
 *   PostgreSQL, Oracle...) "product" entrecomillado distingue mayúsculas: no encontraría la tabla PRODUCT.
 * - @Version: bloqueo optimista. Spring Data añade "WHERE version = ?" a cada UPDATE y lo incrementa;
 *   si otro lo cambió antes, el UPDATE no afecta a ninguna fila -> OptimisticLockingFailureException.
 *   También indica si la entidad es NUEVA: version null -> save() hace INSERT; si no, UPDATE.
 *   @JsonIgnore: es un detalle de persistencia; al cliente le llega a través del ETag.
 *
 * Spring Data R2DBC no modifica el objeto: para fijar la versión crea una copia con withVersion(...).
 * Los records encajan bien con ese modelo inmutable.
 */
@Table
public record Product(@Id String id, String name, String category, BigDecimal price, int stock,
                      @Version @JsonIgnore Long version) {

    /** Constructor canónico: el que usa Spring Data para crear el objeto a partir de una fila. */
    @PersistenceCreator
    public Product {
    }

    /** Producto que aún no se ha guardado (sin versión): save() hará INSERT. */
    public Product(String id, String name, String category, BigDecimal price, int stock) {
        this(id, name, category, price, stock, null);
    }

    public Product withId(String newId) {
        return new Product(newId, name, category, price, stock, version);
    }

    public Product withPrice(BigDecimal newPrice) {
        return new Product(id, name, category, newPrice, stock, version);
    }

    public Product withStock(int newStock) {
        return new Product(id, name, category, price, newStock, version);
    }

    public Product withVersion(Long newVersion) {
        return new Product(id, name, category, price, stock, newVersion);
    }

    public boolean hasStock(int quantity) {
        return stock >= quantity;
    }
}
