-- Día 4: esquema del catálogo y de los pedidos en H2 (lo ejecuta Spring Boot al arrancar: spring.sql.init.*)

CREATE TABLE product (
    id        VARCHAR(36)   PRIMARY KEY,
    name      VARCHAR(60)   NOT NULL,
    category  VARCHAR(40)   NOT NULL,
    price     DECIMAL(10,2) NOT NULL,
    stock     INT           NOT NULL CHECK (stock >= 0),   -- la BD es la última defensa contra el stock negativo
    version   BIGINT        NOT NULL                       -- bloqueo optimista (@Version) y ETag
);

-- "order" es palabra reservada en SQL
CREATE TABLE customer_order (
    id          VARCHAR(36)              PRIMARY KEY,
    customer_id VARCHAR(60)              NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    total       DECIMAL(12,2)            NOT NULL
);

CREATE INDEX idx_customer_order_customer ON customer_order (customer_id);

-- Las líneas son parte del agregado Order: se guardan y se leen SIEMPRE con su pedido.
-- product_id NO es clave ajena a product: la línea guarda una "foto" (nombre, precio) y el producto
-- puede retirarse del catálogo después (el BFF lo muestra como DISCONTINUED).
CREATE TABLE order_detail (
    order_id     VARCHAR(36)   NOT NULL REFERENCES customer_order (id),
    line_no      INT           NOT NULL,
    product_id   VARCHAR(36)   NOT NULL,
    product_name VARCHAR(60)   NOT NULL,
    unit_price   DECIMAL(10,2) NOT NULL,
    quantity     INT           NOT NULL,
    subtotal     DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (order_id, line_no)
);
