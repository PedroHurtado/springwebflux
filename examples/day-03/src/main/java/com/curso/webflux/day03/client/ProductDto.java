package com.curso.webflux.day03.client;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lo que el BFF necesita saber de un producto del microservicio de catálogo.
 *
 * No se reutiliza catalog.Product: dos microservicios NO comparten clases del dominio, solo el
 * contrato HTTP (JSON). Cada consumidor declara únicamente los campos que usa.
 *
 * @JsonIgnoreProperties(ignoreUnknown = true): "tolerant reader". La aplicación activa
 * fail-on-unknown-properties para lo que ENVÍAN sus clientes (application.properties), pero si el
 * catálogo añade mañana un campo nuevo (o ya trae "category", que aquí no se usa) este cliente no
 * debe romperse. Estricto con lo que recibes de tus clientes, tolerante con lo que te devuelven otros servicios.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDto(String id, String name, BigDecimal price, int stock) {
}
