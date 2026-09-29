package com.curso.webflux.day02.orders;

import java.util.List;

import org.springframework.stereotype.Service;

import com.curso.webflux.day02.catalog.ProductRepository;
import com.curso.webflux.day02.orders.DetailCheck.DetailError;
import com.curso.webflux.day02.orders.OrderRequest.DetailRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Alta de pedidos con validación de negocio REACTIVA.
 *
 * Cada línea del pedido hace referencia a un Product por su id. Antes de guardar hay que
 * comprobar, consultando el catálogo (E/S no bloqueante, 50 ms por consulta):
 *   1. que el producto existe;
 *   2. que hay stock suficiente.
 *
 * Se ofrecen dos estrategias para comparar en clase:
 *   - create():         consulta TODAS las líneas en paralelo y devuelve TODOS los errores juntos.
 *   - createFailFast(): consulta las líneas de una en una y se detiene en el PRIMER error.
 *
 * La versión imperativa equivalente (Spring MVC) está en docs/day-02/02-pedidos-validacion-reactiva.md.
 */
@Service
public class OrderService {

    private final ProductRepository products;
    private final OrderRepository orders;

    public OrderService(ProductRepository products, OrderRepository orders) {
        this.products = products;
        this.orders = orders;
    }

    public Flux<Order> findAll() {
        return orders.findAll();
    }

    public Flux<Order> findByCustomer(String customerId) {
        return orders.findByCustomer(customerId);
    }

    public Mono<Order> findById(String id) {
        return orders.findById(id)
                .switchIfEmpty(Mono.error(() -> new OrderNotFoundException(id)));
    }

    /**
     * Estrategia 1: validar todas las líneas y acumular errores.
     *
     * Nada de esto se ejecuta al llamar al método: solo se DESCRIBE el proceso.
     * Se ejecuta cuando el framework se suscribe para escribir la respuesta.
     *
     * El paso ③ es una BARRERA inevitable: las reglas de negocio (devolver todos los errores, no reservar
     * stock si alguna línea falla) exigen conocer el resultado de todas las líneas antes de decidir.
     * No se materializa ninguna lista intermedia: cada resultado se acumula según llega (reduce).
     */
    public Mono<Order> create(OrderRequest request) {
        return Flux.fromIterable(request.details())
                .index()                                                      // ① (posición, línea)
                .flatMapSequential(indexed ->                                 // ② consultas EN PARALELO,
                        checkDetail(indexed.getT1().intValue(), indexed.getT2())) //    resultados EN ORDEN
                .reduce(OrderValidation.empty(), OrderValidation::add)        // ③ acumular válidas y errores
                .flatMap(OrderValidation::toDetailsOrReject)                  // ④ error 422 o List<OrderDetail>
                .map(details -> Order.create(request.customerId(), details))  // ⑤ construir el pedido
                .flatMap(this::reserveStockAndSave);                          // ⑥ efectos: stock + guardar
    }

    /**
     * Valida UNA línea. No lanza excepciones: convierte cada caso en un valor.
     *   - producto encontrado con stock  -> Valid
     *   - producto encontrado sin stock  -> Invalid
     *   - Mono vacío (no existe)         -> Invalid (defaultIfEmpty)
     */
    private Mono<DetailCheck> checkDetail(int line, DetailRequest request) {
        return products.findById(request.productId())
                .map(product -> product.hasStock(request.quantity())
                        ? new DetailCheck.Valid(OrderDetail.of(product, request.quantity()))
                        : invalid(line, request, "stock insuficiente: solicitadas " + request.quantity()
                                + ", disponibles " + product.stock()))
                .defaultIfEmpty(invalid(line, request, "el producto no existe"));
    }

    /**
     * Estrategia 2 (fail-fast): la primera línea incorrecta corta el flujo.
     *
     * concatMap procesa las líneas de una en una y en orden: si la línea 0 falla, las
     * siguientes ni siquiera se consultan (Mono.error cancela lo que queda por hacer).
     * Menos consultas cuando hay errores, pero más lento cuando todo va bien (N x latencia)
     * y el cliente solo se entera de un error cada vez.
     */
    public Mono<Order> createFailFast(OrderRequest request) {
        return Flux.fromIterable(request.details())
                .index()
                .concatMap(indexed -> {
                    int line = indexed.getT1().intValue();
                    DetailRequest detail = indexed.getT2();
                    return products.findById(detail.productId())
                            .switchIfEmpty(Mono.error(() -> rejected(line, detail, "el producto no existe")))
                            .filter(product -> product.hasStock(detail.quantity()))
                            .switchIfEmpty(Mono.error(() -> rejected(line, detail, "stock insuficiente")))
                            .map(product -> OrderDetail.of(product, detail.quantity()));
                })
                .collectList()   // aquí sí: el Order ES una lista de líneas (acotada a 20 por @Size)
                .map(details -> Order.create(request.customerId(), details))
                .flatMap(this::reserveStockAndSave);
    }

    /**
     * Efectos secundarios, solo cuando TODO es válido: descontar stock y guardar el pedido.
     *
     * then(...) encadena "cuando termine lo anterior, haz esto" descartando su valor.
     * Aviso: entre la validación y la reserva otra petición podría consumir el mismo stock.
     * Con una base de datos real se resolvería con una transacción reactiva (TransactionalOperator
     * o @Transactional con R2DBC) y un UPDATE condicional (... WHERE stock >= ?).
     */
    private Mono<Order> reserveStockAndSave(Order order) {
        return Flux.fromIterable(order.details())
                .concatMap(detail -> products.update(detail.productId(),
                        product -> product.withStock(product.stock() - detail.quantity())))
                .then(orders.save(order));
    }

    private static DetailCheck invalid(int line, DetailRequest request, String message) {
        return new DetailCheck.Invalid(new DetailError(line, request.productId(), message));
    }

    private static OrderRejectedException rejected(int line, DetailRequest request, String message) {
        return new OrderRejectedException(List.of(new DetailError(line, request.productId(), message)));
    }
}
