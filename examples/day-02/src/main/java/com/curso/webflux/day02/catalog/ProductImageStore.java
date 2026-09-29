package com.curso.webflux.day02.catalog;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * Almacén en memoria de imágenes de producto (multipart).
 *
 * FilePart.content() es un Flux<DataBuffer>: el fichero llega por trozos, sin cargarlo entero
 * en memoria hasta que nosotros decidimos juntarlo. DataBufferUtils.join(content, max) lo
 * concatena y falla con DataBufferLimitException si supera el tamaño permitido.
 */
@Component
public class ProductImageStore {

    public static final int MAX_IMAGE_BYTES = 256 * 1024;

    private final Map<String, StoredImage> images = new ConcurrentHashMap<>();

    public Mono<StoredImage> store(String productId, FilePart file) {
        MediaType type = file.headers().getContentType();
        return DataBufferUtils.join(file.content(), MAX_IMAGE_BYTES)
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);   // los DataBuffer pueden venir de un pool: liberarlos
                    return new StoredImage(file.filename(), type, bytes);
                })
                .doOnNext(image -> images.put(productId, image));
    }

    public Mono<StoredImage> find(String productId) {
        return Mono.justOrEmpty(images.get(productId));
    }

    public record StoredImage(String filename, MediaType contentType, byte[] bytes) {

        public ImageInfo info(String productId) {
            return new ImageInfo(productId, filename, contentType.toString(), bytes.length);
        }
    }

    public record ImageInfo(String productId, String filename, String contentType, int size) {
    }
}
