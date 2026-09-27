package seekfactory.axoraa.services.serviceImpl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import seekfactory.axoraa.dto.Response.media.MediaUploadResponse;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.services.services.MediaStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Local-disk media storage (dev / single-node deployments).
 *
 * Only allowlisted raster images and video containers are accepted: SVG/HTML would be
 * served from the API origin and could carry script. Files are renamed to random UUID
 * keys, so client-supplied names never touch the filesystem.
 */
@Slf4j
@Service
public class LocalMediaStorageService implements MediaStorageService {

    public static final String PUBLIC_PATH = "/api/v1/media/";

    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp",
            "image/gif", "gif");

    private static final Map<String, String> VIDEO_TYPES = Map.of(
            "video/mp4", "mp4",
            "video/webm", "webm",
            "video/quicktime", "mov");

    /** Public documents (product datasheets): PDF only. */
    private static final Map<String, String> DOCUMENT_TYPES = Map.of("application/pdf", "pdf");

    private static final Map<String, String> EXTENSION_TYPES = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "webp", "image/webp",
            "gif", "image/gif",
            "mp4", "video/mp4",
            "webm", "video/webm",
            "mov", "video/quicktime",
            "pdf", "application/pdf");

    /** Chat attachments: raster images and PDF only. */
    private static final Map<String, String> PRIVATE_TYPES = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp",
            "image/gif", "gif",
            "application/pdf", "pdf");

    private static final Pattern PRIVATE_KEY_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|webp|gif|pdf)$");

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    private static final Pattern KEY_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|webp|gif|mp4|webm|mov|pdf)$");

    private final Path root;
    private final long maxImageBytes;
    private final long maxVideoBytes;

    public LocalMediaStorageService(
            @Value("${app.media.storage-dir:uploads}") String storageDir,
            @Value("${app.media.max-image-size:20MB}") DataSize maxImageSize,
            @Value("${app.media.max-video-size:100MB}") DataSize maxVideoSize) {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
        this.maxImageBytes = maxImageSize.toBytes();
        this.maxVideoBytes = maxVideoSize.toBytes();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create media storage directory " + root, e);
        }
        log.info("Media storage directory: {}", root);
    }

    @Override
    public MediaUploadResponse store(MultipartFile file, String kind) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }

        Map<String, String> allowed;
        long maxBytes;
        if ("image".equals(kind)) {
            allowed = IMAGE_TYPES;
            maxBytes = maxImageBytes;
        } else if ("video".equals(kind)) {
            allowed = VIDEO_TYPES;
            maxBytes = maxVideoBytes;
        } else if ("document".equals(kind)) {
            allowed = DOCUMENT_TYPES;
            maxBytes = maxImageBytes;
        } else {
            throw new BadRequestException("kind must be 'image', 'video' or 'document'");
        }

        String contentType = file.getContentType();
        String extension = contentType == null ? null : allowed.get(contentType.toLowerCase());
        if (extension == null) {
            throw new BadRequestException("Unsupported " + kind + " type: " + (contentType == null ? "unknown" : contentType));
        }
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("File exceeds " + (maxBytes / (1024 * 1024)) + "MB limit");
        }

        String key = UUID.randomUUID() + "." + extension;
        Path target = root.resolve(key);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store upload", e);
        }

        return MediaUploadResponse.builder()
                .url(PUBLIC_PATH + key)
                .contentType(EXTENSION_TYPES.get(extension))
                .size(file.getSize())
                .build();
    }

    @Override
    public Resource load(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new ResourceNotFoundException("Media", "key", key);
        }
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new ResourceNotFoundException("Media", "key", key);
        }
        return new PathResource(file);
    }

    @Override
    public String contentTypeOf(String key) {
        String extension = key.substring(key.lastIndexOf('.') + 1);
        return EXTENSION_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    @Override
    public String storePrivate(String namespace, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String extension = PRIVATE_TYPES.get(contentType);
        if (extension == null) {
            throw new BadRequestException("Only images (PNG, JPG, WEBP, GIF) and PDF files can be attached");
        }
        if (file.getSize() > maxImageBytes) {
            throw new BadRequestException("File exceeds " + (maxImageBytes / (1024 * 1024)) + "MB limit");
        }
        String key = UUID.randomUUID() + "." + extension;
        Path dir = privateDir(namespace);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(dir);
            Files.copy(in, dir.resolve(key), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store attachment", e);
        }
        return key;
    }

    @Override
    public Resource loadPrivate(String namespace, String key) {
        if (key == null || !PRIVATE_KEY_PATTERN.matcher(key).matches()) {
            throw new ResourceNotFoundException("Attachment", "key", key);
        }
        Path dir = privateDir(namespace);
        Path file = dir.resolve(key).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
            throw new ResourceNotFoundException("Attachment", "key", key);
        }
        return new PathResource(file);
    }

    private Path privateDir(String namespace) {
        if (namespace == null || !NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new BadRequestException("Invalid attachment namespace");
        }
        Path dir = root.resolve("private").resolve(namespace).normalize();
        if (!dir.startsWith(root.resolve("private"))) {
            throw new BadRequestException("Invalid attachment namespace");
        }
        return dir;
    }
}
