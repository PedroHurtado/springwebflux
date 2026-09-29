package com.curso.webflux.day02.catalog;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.curso.webflux.day02.catalog.ProductImageStore.ImageInfo;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador anotado (parte II).
 *
 * Novedades respecto al día 1:
 * - data binding de parámetros a un record (@ModelAttribute) con conversión de tipos propia;
 * - validación de parámetros sueltos (@Min/@Max) con la validación de métodos integrada;
 * - errores como ProblemDetail (ver CatalogExceptionHandler);
 * - Location absoluta construida con UriComponentsBuilder;
 * - subida y descarga de ficheros (multipart);
 * - dos versiones del mismo endpoint (API versioning de Spring Framework 7);
 * - CORS a nivel de controlador con @CrossOrigin (se suma a la configuración global de WebConfig).
 */
@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "http://localhost:5173", maxAge = 3600)
public class ProductController {

    private final ProductService service;
    private final ProductImageStore images;

    public ProductController(ProductService service, ProductImageStore images) {
        this.service = service;
        this.images = images;
    }

    /**
     * Accept: application/json   -> array JSON (el Flux se agrega antes de escribir).
     * Accept: application/x-ndjson -> un JSON por línea, escrito en streaming.
     */
    @GetMapping
    public Flux<Product> findAll(@RequestParam(required = false) String category) {
        return service.findAll(category);
    }

    /**
     * GET /api/products/search?category=perifericos&maxPrice=50&sort=price-desc
     * Sin @Valid los datos se enlazan igual, pero no se validan. Errores -> WebExchangeBindException (400).
     */
    @GetMapping("/search")
    public Flux<Product> search(@Valid @ModelAttribute ProductSearch search) {
        return service.search(search);
    }

    /**
     * Validación de métodos integrada (Spring 6.1+): basta con poner restricciones en el parámetro.
     * ?limit=50 -> HandlerMethodValidationException (400).
     */
    @GetMapping("/top")
    public Flux<Product> top(@RequestParam(defaultValue = "3") @Min(1) @Max(10) int limit) {
        return service.top(limit);
    }

    /** Versión 1.0 (la versión por defecto si la petición no envía la cabecera API-Version). */
    @GetMapping(path = "/{id}", version = "1.0")
    public Mono<Product> findById(@PathVariable String id) {
        return service.findById(id);
    }

    /** Versión 2.0: mismo recurso, otra representación. Cabecera "API-Version: 2.0". */
    @GetMapping(path = "/{id}", version = "2.0")
    public Mono<ProductV2> findByIdV2(@PathVariable String id) {
        return service.findById(id).map(ProductV2::from);
    }

    @GetMapping("/count")
    public Mono<Long> count() {
        return service.findAll(null).count();
    }

    /**
     * UriComponentsBuilder como argumento: viene preparado con esquema, host y puerto de la petición
     * actual, así que la cabecera Location es absoluta (http://localhost:8080/api/products/...).
     */
    @PostMapping
    public Mono<ResponseEntity<Product>> create(@Valid @RequestBody Mono<ProductRequest> request,
                                                UriComponentsBuilder uriBuilder) {
        return request
                .flatMap(service::create)
                .map(p -> {
                    URI location = uriBuilder.path("/api/products/{id}").buildAndExpand(p.id()).toUri();
                    return ResponseEntity.created(location).body(p);
                });
    }

    @PutMapping("/{id}")
    public Mono<Product> update(@PathVariable String id, @Valid @RequestBody ProductRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable String id) {
        return service.delete(id);
    }

    /** Server-Sent Events: el navegador lo consume con EventSource. */
    @GetMapping(path = "/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ProductService.PriceChange> prices() {
        return service.priceTicker();
    }

    /**
     * Multipart: curl -F "file=@foto.png" http://localhost:8080/api/products/1/image
     * @RequestPart Mono<FilePart>: el fichero se procesa en streaming, sin bloquear.
     */
    @PostMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ImageInfo> uploadImage(@PathVariable String id, @RequestPart("file") Mono<FilePart> file) {
        return service.findById(id)                                   // 404 si el producto no existe
                .then(file)
                .filter(ProductController::isImage)
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Solo se admiten imágenes (image/*)")))
                .flatMap(part -> images.store(id, part))
                .map(image -> image.info(id));
    }

    @GetMapping("/{id}/image")
    public Mono<ResponseEntity<byte[]>> image(@PathVariable String id) {
        return images.find(id)
                .map(image -> ResponseEntity.ok().contentType(image.contentType()).body(image.bytes()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private static boolean isImage(FilePart part) {
        MediaType type = part.headers().getContentType();
        return type != null && "image".equals(type.getType());
    }
}
