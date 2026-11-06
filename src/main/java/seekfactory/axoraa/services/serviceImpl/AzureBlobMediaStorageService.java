package seekfactory.axoraa.services.serviceImpl;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import seekfactory.axoraa.dto.Response.media.MediaUploadResponse;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.services.media.AzureBlobClient;
import seekfactory.axoraa.services.media.ImageCompressor;
import seekfactory.axoraa.services.media.MediaTypes;
import seekfactory.axoraa.services.media.VideoTranscoder;
import seekfactory.axoraa.services.services.MediaStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Media in a private Azure Blob Storage container. Enabled with {@code app.media.storage=azure}.
 *
 * Blob names follow the S3 layout, so a bucket can be copied across as-is:
 * <ul>
 *   <li>{@code media/<uuid>.<ext>} and {@code media/variants/<name>}: public files. Browsers are
 *       redirected to a read-only SAS link that is shared while it is fresh, so their cache works.</li>
 *   <li>{@code private/<namespace>/<uuid>.<ext>}: chat attachments, behind the API's access check and
 *       a short-lived SAS link.</li>
 * </ul>
 * The container stays private (no anonymous access).
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.media.storage", havingValue = "azure")
public class AzureBlobMediaStorageService implements MediaStorageService {

    static final String PUBLIC_PREFIX = "media/";
    static final String PRIVATE_PREFIX = "private/";
    private static final String VARIANT_PREFIX = PUBLIC_PREFIX + "variants/";
    private static final String IMMUTABLE = "public, max-age=31536000, immutable";
    private static final String NO_CACHE = "no-cache";
    private static final int MAX_SIGNED_LINKS = 50_000;

    private final AzureBlobClient blobs;
    private final Duration signedUrlTtl;
    private final MediaTypes.Limits limits;
    private final ImageCompressor imageCompressor;
    private final VideoTranscoder videoTranscoder;
    private final Path pending;
    private final Path out;
    private final Set<Path> queuedVideos = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, SignedLink> signedLinks = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    private record SignedLink(String url, Instant renewAt) {}

    @Autowired
    public AzureBlobMediaStorageService(
            @Value("${app.media.azure.account:}") String account,
            @Value("${app.media.azure.account-key:}") String accountKey,
            @Value("${app.media.azure.container:media}") String container,
            @Value("${app.media.azure.endpoint:}") String endpoint,
            @Value("${app.media.azure.signed-url-ttl:1h}") Duration signedUrlTtl,
            @Value("${app.media.azure.work-dir:media-work}") String workDir,
            @Value("${app.media.max-image-size:20MB}") DataSize maxImageSize,
            @Value("${app.media.max-video-size:100MB}") DataSize maxVideoSize,
            @Value("${app.media.max-document-size:50MB}") DataSize maxDocumentSize,
            @Value("${app.media.image-max-dimension:2048}") int imageMaxDimension,
            @Value("${app.media.image-quality:0.85}") float imageQuality,
            @Value("${app.media.ffmpeg-path:ffmpeg}") String ffmpegPath,
            @Value("${app.media.video-crf:23}") int videoCrf,
            @Value("${app.media.video-preset:medium}") String videoPreset) {
        this(new AzureBlobClient(account, accountKey, container, endpoint), signedUrlTtl,
                new MediaTypes.Limits(maxImageSize.toBytes(), maxVideoSize.toBytes(), maxDocumentSize.toBytes()),
                new ImageCompressor(imageMaxDimension, imageQuality),
                new VideoTranscoder(ffmpegPath, videoCrf, videoPreset),
                Path.of(workDir));
        checkContainer();
        log.info("Media storage: Azure Blob container '{}' in account '{}'", container, account);
        resumePendingVideos();
    }

    AzureBlobMediaStorageService(AzureBlobClient blobs, Duration signedUrlTtl, MediaTypes.Limits limits,
                                 ImageCompressor imageCompressor, VideoTranscoder videoTranscoder, Path workDir) {
        this.blobs = blobs;
        this.signedUrlTtl = signedUrlTtl;
        this.limits = limits;
        this.imageCompressor = imageCompressor;
        this.videoTranscoder = videoTranscoder;
        Path work = workDir.toAbsolutePath().normalize();
        this.pending = work.resolve("pending");
        this.out = work.resolve("out");
        try {
            Files.createDirectories(pending);
            Files.createDirectories(out);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create media work directory " + work, e);
        }
    }

    @PreDestroy
    void shutdown() {
        videoTranscoder.close();
    }

    // ---- public files ----

    @Override
    public MediaUploadResponse store(MultipartFile file, String kind) {
        MediaTypes.Accepted accepted = MediaTypes.validatePublic(file, kind, limits);
        if (accepted.isImage()) {
            byte[] original = readAll(file);
            Optional<ImageCompressor.Result> compressed = imageCompressor.compress(original, accepted.extension());
            byte[] bytes = compressed.map(ImageCompressor.Result::bytes).orElse(original);
            String key = UUID.randomUUID() + "." + compressed.map(ImageCompressor.Result::extension).orElse(accepted.extension());
            blobs.put(PUBLIC_PREFIX + key, MediaTypes.contentTypeOf(key), IMMUTABLE, bytes);
            return uploaded(key, bytes.length);
        }
        if (accepted.isVideo() && videoTranscoder.isAvailable()) {
            return storeVideo(file, accepted.extension());
        }
        String key = UUID.randomUUID() + "." + accepted.extension();
        putStream(PUBLIC_PREFIX + key, MediaTypes.contentTypeOf(key), IMMUTABLE, file);
        return uploaded(key, file.getSize());
    }

    private MediaUploadResponse storeVideo(MultipartFile file, String extension) {
        String id = UUID.randomUUID().toString();
        String key = id + ".mp4";
        Path source = pending.resolve(id + "." + extension);
        try (VideoTranscoder.Admission admission = videoTranscoder.reserve(pending, file.getSize())) {
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, source, StandardCopyOption.REPLACE_EXISTING);
            }
            blobs.put(PUBLIC_PREFIX + key, MediaTypes.contentTypeOf(source.getFileName().toString()), NO_CACHE, source);
            queueVideo(admission, source, key);
        } catch (IOException e) {
            try { Files.deleteIfExists(source); } catch (IOException ignored) { }
            throw new UncheckedIOException("Failed to store upload", e);
        } catch (RuntimeException e) {
            try { Files.deleteIfExists(source); } catch (IOException ignored) { }
            throw e;
        }
        return MediaUploadResponse.builder()
                .url(MediaTypes.PUBLIC_PATH + key)
                .contentType("video/mp4")
                .size(file.getSize())
                .build();
    }

    private void queueVideo(Path source, String key) {
        if (queuedVideos.contains(source)) return;
        try (var admission = videoTranscoder.reserve()) { queueVideo(admission, source, key); }
    }

    private void queueVideo(VideoTranscoder.Admission admission, Path source, String key) {
        if (!queuedVideos.add(source)) return;
        Path target = out.resolve(key);
        try {
            videoTranscoder.submit(admission, source, target, compressed -> {
                try { finishVideo(source, target, key, compressed); }
                finally { queuedVideos.remove(source); }
            });
        } catch (RuntimeException failure) { queuedVideos.remove(source); throw failure; }
    }

    void finishVideo(Path source, Path target, String key, boolean compressed) {
        try {
            if (compressed) {
                blobs.put(PUBLIC_PREFIX + key, "video/mp4", IMMUTABLE, target);
            } else if (source.getFileName().toString().endsWith(".mp4")) {
                blobs.put(PUBLIC_PREFIX + key, "video/mp4", IMMUTABLE, source);
            }
            Files.deleteIfExists(target);
            Files.deleteIfExists(source);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not finalize video {}: {}", key, e.getMessage());
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void resumePendingVideos() {
        if (!videoTranscoder.isAvailable()) return;
        try (Stream<Path> files = Files.list(pending)) {
            files.filter(Files::isRegularFile).forEach(source -> {
                String name = source.getFileName().toString();
                String id = name.substring(0, name.indexOf('.'));
                try {
                    queueVideo(source, id + ".mp4");
                } catch (java.util.concurrent.RejectedExecutionException busy) {
                    log.debug("Video queue full; pending file retained for the next scan");
                }
            });
        } catch (IOException e) {
            log.warn("Could not scan pending videos: {}", e.getMessage());
        }
    }

    @Override
    public Optional<String> publicUrl(String key) {
        requirePublicKey(key);
        return Optional.of(sharedLink(PUBLIC_PREFIX + key));
    }

    @Override
    public long publicRedirectMaxAgeSeconds() {
        return Math.max(0, Math.min(300, signedUrlTtl.getSeconds() - 30));
    }

    @Override
    public Resource load(String key) {
        requirePublicKey(key);
        String blob = PUBLIC_PREFIX + key;
        return new BlobResource(blob, blobs.head(blob).orElseThrow(() -> new ResourceNotFoundException("Media", "key", key)));
    }

    @Override
    public String contentTypeOf(String key) {
        return MediaTypes.contentTypeOf(key);
    }

    @Override
    public void downloadPublic(String key, Path target) {
        requirePublicKey(key);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        blobs.download(PUBLIC_PREFIX + key, target);
    }

    @Override
    public void replacePublic(String key, Path source) {
        requirePublicKey(key);
        blobs.put(PUBLIC_PREFIX + key, MediaTypes.contentTypeOf(key), IMMUTABLE, source);
    }

    @Override
    public byte[] readPublic(String key) {
        requirePublicKey(key);
        return blobs.getBytes(PUBLIC_PREFIX + key);
    }

    @Override
    public boolean variantExists(String name) {
        return blobs.head(VARIANT_PREFIX + name).isPresent();
    }

    @Override
    public void storeVariant(String name, byte[] bytes, String contentType) {
        blobs.put(VARIANT_PREFIX + name, contentType, IMMUTABLE, bytes);
    }

    @Override
    public Optional<String> variantUrl(String name) {
        return Optional.of(sharedLink(VARIANT_PREFIX + name));
    }

    @Override
    public boolean isProcessing(String key) {
        if (key == null || !MediaTypes.KEY_PATTERN.matcher(key).matches() || !key.endsWith(".mp4")) return false;
        String id = key.substring(0, key.length() - ".mp4".length());
        return MediaTypes.VIDEO_TYPES.values().stream().anyMatch(ext -> Files.isRegularFile(pending.resolve(id + "." + ext)));
    }

    @Override
    public void delete(String key) {
        if (key == null || !MediaTypes.KEY_PATTERN.matcher(key).matches()) return;
        try {
            blobs.delete(PUBLIC_PREFIX + key);
        } catch (RuntimeException e) {
            log.warn("Could not delete media {}: {}", key, e.getMessage());
        }
    }

    // ---- private files ----

    @Override
    public String storePrivate(String namespace, MultipartFile file) {
        MediaTypes.requireNamespace(namespace);
        MediaTypes.Accepted accepted = MediaTypes.validatePrivate(file, limits);
        if (accepted.isImage()) {
            byte[] original = readAll(file);
            Optional<ImageCompressor.Result> compressed = imageCompressor.compress(original, accepted.extension());
            String key = UUID.randomUUID() + "." + compressed.map(ImageCompressor.Result::extension).orElse(accepted.extension());
            blobs.put(privateBlob(namespace, key), MediaTypes.contentTypeOf(key), null,
                    compressed.map(ImageCompressor.Result::bytes).orElse(original));
            return key;
        }
        String key = UUID.randomUUID() + "." + accepted.extension();
        putStream(privateBlob(namespace, key), MediaTypes.contentTypeOf(key), null, file);
        return key;
    }

    @Override
    public Resource loadPrivate(String namespace, String key) {
        MediaTypes.requireNamespace(namespace);
        if (key == null || !MediaTypes.PRIVATE_KEY_PATTERN.matcher(key).matches()) {
            throw new ResourceNotFoundException("Attachment", "key", key);
        }
        String blob = privateBlob(namespace, key);
        return new BlobResource(blob, blobs.head(blob).orElseThrow(() -> new ResourceNotFoundException("Attachment", "key", key)));
    }

    @Override
    public Optional<String> privateUrl(String namespace, String key) {
        MediaTypes.requireNamespace(namespace);
        if (key == null || !MediaTypes.PRIVATE_KEY_PATTERN.matcher(key).matches()) {
            throw new ResourceNotFoundException("Attachment", "key", key);
        }
        String contentType = MediaTypes.contentTypeOf(key);
        boolean viewable = contentType.startsWith("image/") || "application/pdf".equals(contentType);
        // Chat attachments get a short link: it is handed out only after the access check
        return Optional.of(blobs.readUrl(privateBlob(namespace, key), Duration.ofMinutes(10), contentType,
                viewable ? "inline" : "attachment"));
    }

    // ---- plumbing ----

    private void putStream(String blob, String contentType, String cacheControl, MultipartFile file) {
        try {
            InputStream in = file.getInputStream();
            blobs.put(blob, contentType, cacheControl,
                    HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.ofInputStream(() -> in), file.getSize()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store upload", e);
        }
    }

    /** One link per file while it is fresh, so browsers and CDNs can reuse their cached copy. */
    private String sharedLink(String blob) {
        Duration reuse = signedUrlTtl.dividedBy(2);
        if (reuse.isZero() || reuse.isNegative()) return blobs.readUrl(blob, signedUrlTtl, null, null);
        Instant now = clock.instant();
        if (signedLinks.size() > MAX_SIGNED_LINKS) {
            signedLinks.values().removeIf(link -> !now.isBefore(link.renewAt()));
            if (signedLinks.size() > MAX_SIGNED_LINKS) signedLinks.clear();
        }
        return signedLinks.compute(blob, (k, link) -> link != null && now.isBefore(link.renewAt())
                ? link
                : new SignedLink(blobs.readUrl(blob, signedUrlTtl, null, null), now.plus(reuse))).url();
    }

    /** Fails start-up early when the account, key or container is wrong. */
    private void checkContainer() {
        String probe = PUBLIC_PREFIX + ".startup-check";
        try {
            blobs.put(probe, "text/plain", NO_CACHE, "ok".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            blobs.head(probe).orElseThrow();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Azure Blob container '" + blobs.container() + "' is not reachable with "
                    + "APP_MEDIA_AZURE_ACCOUNT / APP_MEDIA_AZURE_ACCOUNT_KEY: " + e.getMessage(), e);
        }
    }

    static String privateBlob(String namespace, String key) {
        return PRIVATE_PREFIX + namespace + "/" + key;
    }

    private static void requirePublicKey(String key) {
        if (key == null || !MediaTypes.KEY_PATTERN.matcher(key).matches()) {
            throw new ResourceNotFoundException("Media", "key", key);
        }
    }

    private static MediaUploadResponse uploaded(String key, long size) {
        return MediaUploadResponse.builder()
                .url(MediaTypes.PUBLIC_PATH + key)
                .contentType(MediaTypes.contentTypeOf(key))
                .size(size)
                .build();
    }

    private static byte[] readAll(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload", e);
        }
    }

    private final class BlobResource extends AbstractResource {
        private final String blob;
        private final AzureBlobClient.Properties properties;

        BlobResource(String blob, AzureBlobClient.Properties properties) {
            this.blob = blob;
            this.properties = properties;
        }

        @Override
        public InputStream getInputStream() {
            return blobs.get(blob);
        }

        @Override
        public long contentLength() {
            return properties.contentLength();
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public String getFilename() {
            return blob.substring(blob.lastIndexOf('/') + 1);
        }

        @Override
        public String getDescription() {
            return "azure-blob://" + blobs.container() + "/" + blob;
        }
    }
}
