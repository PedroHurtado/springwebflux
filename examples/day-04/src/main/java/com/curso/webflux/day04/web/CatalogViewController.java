package com.curso.webflux.day04.web;

import java.time.Duration;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.Rendering;
import org.thymeleaf.spring6.context.webflux.ReactiveDataDriverContextVariable;

import com.curso.webflux.day04.catalog.Product;
import com.curso.webflux.day04.catalog.ProductNotFoundException;
import com.curso.webflux.day04.catalog.ProductService;
import com.curso.webflux.day04.catalog.ProductV2;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Día 4 — Tecnologías para las vistas: el catálogo como páginas HTML generadas en el servidor (Thymeleaf).
 *
 * @Controller (no @RestController): lo que devuelve el método es el NOMBRE DE UNA VISTA. El
 * ViewResolutionResultHandler lo pasa al ThymeleafReactiveViewResolver, que busca
 * src/main/resources/templates/{nombre}.html.
 *
 * Cuatro formas de hacerlo, de la más sencilla a la más "reactiva":
 *   1. list:   Model con un Flux -> WebFlux lo RESUELVE (espera a todos los elementos) y luego renderiza.
 *   2. live:   ReactiveDataDriverContextVariable -> la página se envía POR TROZOS según llegan los datos.
 *   3. detail: Mono<Rendering> -> vista + modelo + estado HTTP en un único objeto.
 *   4. form:   formulario con data binding, Bean Validation y redirección (patrón Post/Redirect/Get).
 */
@Controller
@RequestMapping("/catalog")
public class CatalogViewController {

    /** Retraso artificial de la página "live" para VER cómo llega por trozos. */
    static final Duration LIVE_DELAY = Duration.ofMillis(400);

    private final ProductService service;

    public CatalogViewController(ProductService service) {
        this.service = service;
    }

    /**
     * 1. Modo normal ("full"). Se puede meter un Flux o un Mono en el Model: antes de renderizar, WebFlux
     * espera a que se resuelvan (un Flux se convierte en List) y la plantilla recibe los VALORES.
     * Es una barrera inevitable en este modo: Thymeleaf necesita todos los datos para generar la página
     * de una vez. Si la lista puede ser larga o lenta, mejor el modo data-driven (live).
     */
    @GetMapping
    public String list(@RequestParam(required = false) String category, Model model) {
        model.addAttribute("products", service.findAll(category));
        model.addAttribute("category", category);
        return "catalog/list";
    }

    /**
     * 2. Modo data-driven: el Flux NO se resuelve antes. Thymeleaf empieza a escribir la página (cabecera,
     * inicio de la tabla...) y, cada vez que llega un producto, procesa su fila y la envía (chunked). El
     * navegador va pintando filas según llegan. El 1 es el tamaño del buffer: se envía cada elemento.
     * Solo puede haber UNA variable data-driven por plantilla.
     */
    @GetMapping("/live")
    public String live(Model model) {
        Flux<Product> slowProducts = service.findAll(null).delayElements(LIVE_DELAY);
        model.addAttribute("products", new ReactiveDataDriverContextVariable(slowProducts, 1));
        return "catalog/live";
    }

    /**
     * 3. Rendering: la alternativa "de API" a String + Model. Reúne vista, atributos, estado y cabeceras.
     * El Mono<Rendering> permite decidir la vista cuando llegan los datos. Si el producto no existe,
     * findById emite ProductNotFoundException, que recoge el @ExceptionHandler de ESTE controlador.
     */
    @GetMapping("/{id}")
    public Mono<Rendering> detail(@PathVariable String id) {
        return service.findById(id)
                .map(product -> Rendering.view("catalog/detail")
                        .modelAttribute("product", product)
                        .modelAttribute("priceWithVat", ProductV2.from(product).priceWithVat())
                        .build());
    }

    /**
     * Error en una PÁGINA: se responde con otra página (404), no con el ProblemDetail JSON de la API.
     * Un @ExceptionHandler del propio controlador tiene prioridad sobre el @RestControllerAdvice global.
     */
    @ExceptionHandler
    public Rendering notFound(ProductNotFoundException ex) {
        return Rendering.view("catalog/not-found")
                .modelAttribute("productId", ex.getProductId())
                .status(HttpStatus.NOT_FOUND)
                .build();
    }

    /** 4a. Formulario vacío. */
    @GetMapping("/new")
    public String newProduct(Model model) {
        model.addAttribute("product", new ProductForm());
        return "catalog/form";
    }

    /**
     * 4b. Envío del formulario (application/x-www-form-urlencoded).
     *
     * @ModelAttribute enlaza los campos del formulario con ProductForm; @Valid lo valida y BindingResult
     * (justo detrás) recoge los errores en lugar de lanzar una excepción. Con errores se vuelve a mostrar
     * el formulario: th:field conserva lo escrito y th:errors muestra los mensajes.
     * Si todo va bien: "redirect:" -> 303 See Other a la ficha del producto (Post/Redirect/Get: recargar la
     * página no vuelve a enviar el formulario).
     */
    @PostMapping
    public Mono<String> create(@Valid @ModelAttribute("product") ProductForm form, BindingResult result) {
        if (result.hasErrors()) {
            return Mono.just("catalog/form");
        }
        return service.create(form.toRequest())
                .map(product -> "redirect:/catalog/" + product.id());
    }
}
