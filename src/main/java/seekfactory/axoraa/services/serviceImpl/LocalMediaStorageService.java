package seekfactory.axoraa.services.serviceImpl;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import seekfactory.axoraa.dto.Response.media.MediaUploadResponse;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.services.media.ImageCompressor;
import seekfactory.axoraa.services.media.MediaTypes;
import seekfactory.axoraa.services.media.VideoTranscoder;
import seekfactory.axoraa.services.services.MediaStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.UUID;

/**
 * Local-disk media storage (dev / single-node deployments).
 *
 * Only allowlisted raster images, video containers and document/CAD formats are accepted:
 * SVG/HTML would be served from the API origin and could carry script. Files are renamed to
 * random UUID keys, so client-supplied names never touch the filesystem.
 *
 * Photos are compressed on upload (see {@link ImageCompressor}); videos are compressed in the
 * background (see {@link VideoTranscoder}). A video's URL is final from the start: while it is
 * being encoded the key serves the original from {@code pending/}, then the smaller MP4 replaces it.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.media.storage", havingValue = "local", matchIfMissing = true)
public class LocalMediaStorageService implements MediaStorageService {

    public static final String PUBLIC_PATH = MediaTypes.PUBLIC_PATH;

    private final Path root;
    private final MediaTypes.Limits limits;
    /** Originals of videos still being compressed, named {@code <uuid>.<original ext>}. */
    private final Path pending;
    private final ImageCompressor imageCompressor;
    private final VideoTranscoder videoTranscoder;

    public LocalMediaStorageService(
            @Value("${app.media.storage-dir:uploads}") String storageDir,
            @Value("${app.media.max-image-size:20MB}") DataSize maxImageSize,
            @Value("${app.media.max-video-size:100MB}") DataSize maxVideoSize,
            @Value("${app.media.max-document-size:50MB}") DataSize maxDocumentSize,
            @Value("${app.media.image-max-dimension:2048}") int imageMaxDimension,
            @Value("${app.media.image-quality:0.85}") float imageQuality,
            @Value("${app.media.ffmpeg-path:ffmpeg}") String ffmpegPath,
            @Value("${app.media.video-crf:23}") int videoCrf,
            @Value("${app.media.video-preset:medium}") String videoPreset) {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
        this.pending = root.resolve("pending");
        this.limits = new MediaTypes.Limits(maxImageSize.toBytes(), maxVideoSize.toBytes(), maxDocumentSize.toBytes());
        this.imageCompressor = new ImageCompressor(imageMaxDimension, imageQuality);
        this.videoTranscoder = new VideoTranscoder(ffmpegPath, videoCrf, videoPreset);
        try {
            Files.createDirectories(root);
            Files.createDirectories(pending);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create media storage directory " + root, e);
        }
        log.info("Media storage directory: {}", root);
        resumePendingVideos();
    }

    @PreDestroy
    void shutdown() {
        videoTranscoder.close();
    }

    @Override
    public MediaUploadResponse store(MultipartFile file, String kind) {
        MediaTypes.Accepted accepted = MediaTypes.validatePublic(file, kind, limits);
        if (accepted.isImage()) {
            return writeImage(file, accepted.extension());
        }
        if (accepted.isVideo()) {
            return writeVideo(file, accepted.extension());
        }
        return write(file, accepted.extension());
    }

    /** Stores the compressed photo when it is smaller, otherwise the original. */
    private MediaUploadResponse writeImage(MultipartFile file, String extension) {
        byte[] original = readAll(file);
        Optional<ImageCompressor.Result> compressed = imageCompressor.compress(original, extension);
        byte[] bytes = compressed.map(ImageCompressor.Result::bytes).orElse(original);
        String ext = compressed.map(ImageCompressor.Result::extension).orElse(extension);
        String key = UUID.randomUUID() + "." + ext;
        try {
            Files.write(root.resolve(key), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store upload", e);
        }
        compressed.ifPresent(c -> log.info("Image {} compressed {} KB -> {} KB", key,
                original.length / 1024, c.bytes().length / 1024));
        return MediaUploadResponse.builder()
                .url(PUBLIC_PATH + key)
                .contentType(MediaTypes.contentTypeOf(key))
                .size(bytes.length)
                .build();
    }

    /**
     * Without ffmpeg the video is stored as uploaded. With it, the original waits in pending/
     * (served under the final key) until the compressed MP4 is ready.
     */
    private MediaUploadResponse writeVideo(MultipartFile file, String extension) {
        if (!videoTranscoder.isAvailable()) {
            return write(file, extension);
        }
        String id = UUID.randomUUID().toString();
        String key = id + ".mp4";
        Path source = pending.resolve(id + "." + extension);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, source, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store upload", e);
        }
        queueVideo(source, key);
        return MediaUploadResponse.builder()
                .url(PUBLIC_PATH + key)
                .contentType("video/mp4")
                .size(file.getSize())
                .build();
    }

    private void queueVideo(Path source, String key) {
        Path target = root.resolve(key);
        videoTranscoder.submit(source, target, compressed -> finishVideo(source, target, compressed));
    }

    /**
     * After encoding: drop the original when the MP4 replaced it; otherwise an original MP4 is
     * promoted to the final key, and other containers keep serving from pending/.
     */
    private void finishVideo(Path source, Path target, boolean compressed) {
        try {
            if (compressed) {
                Files.deleteIfExists(source);
            } else if (source.getFileName().toString().endsWith(".mp4")) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // e.g. the original is still being streamed on Windows; retried at next startup
            log.warn("Could not finalize video {}: {}", target.getFileName(), e.getMessage());
        }
    }

    /** Re-queues videos whose compression was interrupted by a restart; cleans up finished ones. */
    private void resumePendingVideos() {
        try (Stream<Path> files = Files.list(pending)) {
            files.filter(Files::isRegularFile).forEach(source -> {
                String name = source.getFileName().toString();
                String id = name.substring(0, name.indexOf('.'));
                Path target = root.resolve(id + ".mp4");
                if (Files.isRegularFile(target)) {
                    try {
                        Files.deleteIfExists(source);
                    } catch (IOException ignored) {
                        // leave it for the next start
                    }
                } else if (videoTranscoder.isAvailable()) {
                    log.info("Resuming compression of video {}", id);
                    queueVideo(source, id + ".mp4");
                }
            });
        } catch (IOException e) {
            log.warn("Could not scan pending videos: {}", e.getMessage());
        }
    }

    /** Original of a video still in pending/, if the final file does not exist yet. */
    private Optional<Path> pendingOriginal(String key) {
        if (!key.endsWith(".mp4") || Files.isRegularFile(root.resolve(key))) return Optional.empty();
        String id = key.substring(0, key.length() - ".mp4".length());
        for (String ext : MediaTypes.VIDEO_TYPES.values()) {
            Path candidate = pending.resolve(id + "." + ext);
            if (Files.isRegularFile(candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    private static byte[] readAll(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload", e);
        }
    }

    private MediaUploadResponse write(MultipartFile file, String extension) {
        String key = UUID.randomUUID() + "." + extension;
        Path target = root.resolve(key);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store upload", e);
        }

        return MediaUploadResponse.builder()
                .url(PUBLIC_PATH + key)
                .contentType(MediaTypes.contentTypeOf(key))
                .size(file.getSize())
                .build();
    }

    @Override
    public Resource load(String key) {
        if (key == null || !MediaTypes.KEY_PATTERN.matcher(key).matches()) {
            throw new ResourceNotFoundException("Media", "key", key);
        }
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root)) {
            throw new ResourceNotFoundException("Media", "key", key);
        }
        if (!Files.isRegularFile(file)) {
            return pendingOriginal(key).map(PathResource::new)
                    .orElseThrow(() -> new ResourceNotFoundException("Media", "key", key));
        }
        return new PathResource(file);
    }

    @Override
    public String contentTypeOf(String key) {
        String name = pendingOriginal(key).map(p -> p.getFileName().toString()).orElse(key);
        return MediaTypes.contentTypeOf(name);
    }

    @Override
    public boolean isProcessing(String key) {
        return key != null && MediaTypes.KEY_PATTERN.matcher(key).matches() && pendingOriginal(key).isPresent();
    }

    @Override
    public void delete(String key) {
        if (key == null || !MediaTypes.KEY_PATTERN.matcher(key).matches()) return;
        try {
            Files.deleteIfExists(root.resolve(key).normalize());
        } catch (IOException e) {
            log.warn("Could not delete media {}: {}", key, e.getMessage());
        }
    }

    @Override
    public String storePrivate(String namespace, MultipartFile file) {
        MediaTypes.Accepted accepted = MediaTypes.validatePrivate(file, limits);
        String extension = accepted.extension();
        Path dir = privateDir(namespace);
        try {
            Files.createDirectories(dir);
            if (accepted.isImage()) {
                // Photos in chat are compressed like public ones
                byte[] original = file.getBytes();
                Optional<ImageCompressor.Result> compressed = imageCompressor.compress(original, extension);
                String key = UUID.randomUUID() + "." + compressed.map(ImageCompressor.Result::extension).orElse(extension);
                Files.write(dir.resolve(key), compressed.map(ImageCompressor.Result::bytes).orElse(original));
                return key;
            }
            String key = UUID.randomUUID() + "." + extension;
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(key), StandardCopyOption.REPLACE_EXISTING);
            }
            return key;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store attachment", e);
        }
    }

    @Override
    public Resource loadPrivate(String namespace, String key) {
        if (key == null || !MediaTypes.PRIVATE_KEY_PATTERN.matcher(key).matches()) {
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
        MediaTypes.requireNamespace(namespace);
        Path dir = root.resolve("private").resolve(namespace).normalize();
        if (!dir.startsWith(root.resolve("private"))) {
            throw new BadRequestException("Invalid attachment namespace");
        }
        return dir;
    }
}
