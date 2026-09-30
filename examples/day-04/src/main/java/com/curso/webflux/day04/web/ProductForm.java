package com.curso.webflux.day04.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.curso.webflux.day04.catalog.ProductRequest;

/**
 * Objeto de formulario (form backing object) de la página "Nuevo producto".
 *
 * ¿Por qué una clase con getters/setters y no el record ProductRequest de la API? Porque el formulario
 * tiene que poder REPINTARSE con lo que escribió el usuario aunque no sea válido (precio "abc", nombre
 * vacío...): th:field lee y escribe propiedades JavaBean, y un objeto mutable admite valores a medias.
 * Los mensajes de error se muestran en español con el atributo message de cada restricción.
 */
public class ProductForm {

    @NotBlank(message = "el nombre es obligatorio")
    @Size(max = 60, message = "máximo 60 caracteres")
    private String name;

    @NotBlank(message = "la categoría es obligatoria")
    private String category;

    @NotNull(message = "el precio es obligatorio")
    @Positive(message = "el precio debe ser mayor que 0")
    private BigDecimal price;

    @NotNull(message = "el stock es obligatorio")
    @PositiveOrZero(message = "el stock no puede ser negativo")
    private Integer stock;

    public ProductRequest toRequest() {
        return new ProductRequest(name, category, price, stock);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }
}
