package seekfactory.axoraa.services.services;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import seekfactory.axoraa.dto.Response.media.MediaUploadResponse;

import java.util.Optional;

/**
 * Stores uploaded product photos, seek videos, documents and private chat attachments.
 *
 * Two implementations, chosen with {@code app.media.storage}:
 * <ul>
 *   <li>{@code local} (default): files on this server's disk, served by the API itself.</li>
 *   <li>{@code s3}: an S3-compatible bucket (Alibaba OSS in production) behind a CDN; public files
 *       load straight from the CDN and private ones through short-lived signed links.</li>
 * </ul>
 * Stored references stay {@code /api/v1/media/<key>} either way, so switching storage needs no
 * database changes.
 */
public interface MediaStorageService {

    /**
     * Validate and persist an upload.
     *
     * @param kind "image", "video" or "document" (PDF / CAD / office files)
     */
    MediaUploadResponse store(MultipartFile file, String kind);

    /** Resolve a stored media key for serving; throws ResourceNotFoundException if absent. */
    Resource load(String key);

    /** Content type to serve a stored key with (derived from its allowlisted extension). */
    String contentTypeOf(String key);

    /**
     * True while an uploaded video is still being compressed: the key then serves the original,
     * which must not be cached as the final, immutable file.
     */
    default boolean isProcessing(String key) {
        return false;
    }

    /**
     * Where browsers should load a public file from when this API does not serve it itself
     * (e.g. the CDN in front of a bucket). Empty means "serve it through {@link #load}".
     */
    default Optional<String> publicUrl(String key) {
        return Optional.empty();
    }

    /** Browser lifetime for a public redirect; zero keeps unknown storage implementations revalidating. */
    default long publicRedirectMaxAgeSeconds() {
        return 0;
    }

    /**
     * A short-lived signed link to a private file, for storage that supports it. The caller must
     * have checked the user's access first. Empty means "stream it through {@link #loadPrivate}".
     */
    default Optional<String> privateUrl(String namespace, String key) {
        return Optional.empty();
    }

    // ---- image variants (smaller copies of public photos, see MediaVariantService) ----
    // A variant name is "<uuid>-w<width>.<jpg|png>"; variants live apart from uploads and are
    // never listed or validated as upload keys. Storage without variant support serves originals.

    /** Bytes of a stored public file, to derive a variant from. */
    default byte[] readPublic(String key) {
        throw new UnsupportedOperationException("Variants are not supported by this storage");
    }

    default boolean variantExists(String name) {
        return false;
    }

    default void storeVariant(String name, byte[] bytes, String contentType) {
    }

    /** A stored variant's bytes, when this API serves it itself. */
    default Optional<Resource> loadVariant(String name) {
        return Optional.empty();
    }

    /** Where browsers load a stored variant from (e.g. the CDN); empty means serve it via {@link #loadVariant}. */
    default Optional<String> variantUrl(String name) {
        return Optional.empty();
    }

    // ---- rewriting stored videos in place (see VideoOptimizer) ----

    /** Copy a stored public file to a local path. */
    default void downloadPublic(String key, java.nio.file.Path target) {
        throw new UnsupportedOperationException("Not supported by this storage");
    }

    /** Replace a stored public file's content, keeping its key (and so every link to it). */
    default void replacePublic(String key, java.nio.file.Path source) {
        throw new UnsupportedOperationException("Not supported by this storage");
    }

    /** Remove a public file (best effort; unknown keys are ignored). */
    default void delete(String key) {
    }

    /**
     * Store a private file (chat attachment: image or PDF) under a namespace such as a
     * conversation id. Private files are never served by the public media endpoint.
     *
     * @return the generated key (e.g. {@code <uuid>.pdf})
     */
    String storePrivate(String namespace, MultipartFile file);

    /** Load a private file; throws ResourceNotFoundException if the key is not in that namespace. */
    Resource loadPrivate(String namespace, String key);
}
