package com.curso.webflux.day04.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Día 4: repositorio de pedidos con DatabaseClient (SQL explícito), el nivel más bajo de Spring R2DBC.
 *
 * ¿Por qué no un repositorio de Spring Data como ProductRepository? Porque un Order es un AGREGADO en dos
 * tablas (customer_order + order_detail) y Spring Data R2DBC NO gestiona relaciones (no hay @OneToMany
 * ni carga "lazy" como en JPA). Se hace a mano:
 *   - guardar: INSERT de la cabecera + un INSERT por línea (dentro de la transacción de OrderService);
 *   - leer: UNA consulta con JOIN, ordenada por pedido, y las filas se agrupan por pedido al vuelo.
 *
 * Mismo contrato que el repositorio en memoria del día 3: el servicio y los controladores no cambian.
 */
@Repository
public class OrderRepository {

    private static final String SELECT = """
            SELECT o.id, o.customer_id, o.created_at, o.total,
                   d.line_no, d.product_id, d.product_name, d.unit_price, d.quantity, d.subtotal
            FROM customer_order o JOIN order_detail d ON d.order_id = o.id
            """;
    private static final String ORDER_BY = " ORDER BY o.created_at, o.id, d.line_no";

    private final DatabaseClient db;

    public OrderRepository(DatabaseClient db) {
        this.db = db;
    }

    public Flux<Order> findAll() {
        return toOrders(db.sql(SELECT + ORDER_BY));
    }

    public Flux<Order> findByCustomer(String customerId) {
        return toOrders(db.sql(SELECT + " WHERE o.customer_id = :customerId" + ORDER_BY).bind("customerId", customerId));
    }

    public Mono<Order> findById(String id) {
        return toOrders(db.sql(SELECT + " WHERE o.id = :id" + ORDER_BY).bind("id", id)).next();
    }

    /**
     * Guarda la cabecera y después las líneas, en orden (concatMap). No abre transacción: la abre quien
     * llama (OrderService), que además reserva stock en la MISMA transacción.
     */
    public Mono<Order> save(Order order) {
        Order toSave = order.id() == null ? order.withId(UUID.randomUUID().toString()) : order;
        Mono<Long> header = db.sql("""
                        INSERT INTO customer_order (id, customer_id, created_at, total)
                        VALUES (:id, :customerId, :createdAt, :total)""")
                .bind("id", toSave.id())
                .bind("customerId", toSave.customerId())
                .bind("createdAt", toSave.createdAt())
                .bind("total", toSave.total())
                .fetch().rowsUpdated();
        Flux<Long> lines = Flux.fromIterable(toSave.details())
                .index()
                .concatMap(line -> insertDetail(toSave.id(), line.getT1().intValue(), line.getT2()));
        return header.thenMany(lines).then(Mono.just(toSave));
    }

    private Mono<Long> insertDetail(String orderId, int lineNo, OrderDetail detail) {
        return db.sql("""
                        INSERT INTO order_detail (order_id, line_no, product_id, product_name, unit_price, quantity, subtotal)
                        VALUES (:orderId, :lineNo, :productId, :productName, :unitPrice, :quantity, :subtotal)""")
                .bind("orderId", orderId)
                .bind("lineNo", lineNo)
                .bind("productId", detail.productId())
                .bind("productName", detail.productName())
                .bind("unitPrice", detail.unitPrice())
                .bind("quantity", detail.quantity())
                .bind("subtotal", detail.subtotal())
                .fetch().rowsUpdated();
    }

    /**
     * Filas del JOIN (una por LÍNEA) -> pedidos.
     *
     * Las filas llegan ordenadas por pedido, así que las de un mismo pedido son consecutivas:
     * bufferUntilChanged junta las filas mientras el id no cambie y emite el grupo al cambiar.
     * Es la única "materialización" y está acotada: las líneas de UN pedido (máximo 20, @Size en
     * OrderRequest), que el record Order necesita completas. Los pedidos se siguen emitiendo de uno
     * en uno: nunca está la tabla entera en memoria.
     */
    private static Flux<Order> toOrders(DatabaseClient.GenericExecuteSpec query) {
        return query.map(OrderRepository::toRow).all()
                .bufferUntilChanged(OrderRow::orderId)
                .map(OrderRepository::toOrder);
    }

    private static OrderRow toRow(Readable row) {
        OrderDetail detail = new OrderDetail(row.get("product_id", String.class), row.get("product_name", String.class),
                row.get("unit_price", BigDecimal.class), row.get("quantity", Integer.class),
                row.get("subtotal", BigDecimal.class));
        return new OrderRow(row.get("id", String.class), row.get("customer_id", String.class),
                row.get("created_at", Instant.class), row.get("total", BigDecimal.class), detail);
    }

    private static Order toOrder(List<OrderRow> rows) {
        OrderRow first = rows.get(0);
        return new Order(first.orderId(), first.customerId(), first.createdAt(),
                rows.stream().map(OrderRow::detail).toList(), first.total());
    }

    /** Una fila del JOIN: datos de la cabecera repetidos + una línea. */
    private record OrderRow(String orderId, String customerId, Instant createdAt, BigDecimal total, OrderDetail detail) {
    }
}
