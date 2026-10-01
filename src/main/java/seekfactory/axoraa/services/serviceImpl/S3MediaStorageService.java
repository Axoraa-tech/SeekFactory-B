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
import seekfactory.axoraa.services.media.ImageCompressor;
import seekfactory.axoraa.services.media.MediaTypes;
import seekfactory.axoraa.services.media.VideoTranscoder;
import seekfactory.axoraa.services.services.MediaStorageService;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Media in an S3-compatible bucket: Alibaba OSS in production, MinIO for local testing; AWS S3,
 * Tencent COS or Cloudflare R2 work the same way. Enabled with {@code app.media.storage=s3}.
 *
 * Object keys:
 * <ul>
 *   <li>{@code media/<uuid>.<ext>}: public files (product photos, seek videos, datasheets), served
 *       by the CDN at {@code <public-base-url>/media/<key>}. The bucket itself stays private; the CDN
 *       reads it with origin access.</li>
 *   <li>{@code private/<namespace>/<uuid>.<ext>}: chat attachments. The API checks the user may see
 *       the file, then redirects to a signed link that expires after {@code signed-url-ttl}.</li>
 * </ul>
 *
 * Photos are compressed before upload. A video is uploaded as-is first (so its link works
 * immediately, marked no-cache) and replaced by the compressed MP4 once ffmpeg finishes; the
 * local work copy lives in {@code work-dir} and is resumed after a restart.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.media.storage", havingValue = "s3")
public class S3MediaStorageService implements MediaStorageService {

    static final String PUBLIC_PREFIX = "media/";
    static final String PRIVATE_PREFIX = "private/";
    private static final String IMMUTABLE = "public, max-age=31536000, immutable";
    private static final String NO_CACHE = "no-cache";

    /** Connection and behaviour settings, from {@code app.media.s3.*}. */
    record Settings(String bucket, String publicBaseUrl, Duration signedUrlTtl) {}

    private final S3Client s3;
    private final S3Presigner presigner;
    private final Settings settings;
    private final MediaTypes.Limits limits;
    private final ImageCompressor imageCompressor;
    private final VideoTranscoder videoTranscoder;
    /** Local copies of videos being compressed: {@code pending/<uuid>.<ext>} and {@code out/<uuid>.mp4}. */
    private final Path pending;
    private final Path out;

    @Autowired
    public S3MediaStorageService(
            @Value("${app.media.s3.endpoint:}") String endpoint,
            @Value("${app.media.s3.region:us-east-1}") String region,
            @Value("${app.media.s3.bucket:}") String bucket,
            @Value("${app.media.s3.access-key:}") String accessKey,
            @Value("${app.media.s3.secret-key:}") String secretKey,
            @Value("${app.media.s3.path-style:false}") boolean pathStyle,
            @Value("${app.media.s3.public-base-url:}") String publicBaseUrl,
            @Value("${app.media.s3.signed-url-ttl:10m}") Duration signedUrlTtl,
            @Value("${app.media.s3.work-dir:media-work}") String workDir,
            @Value("${app.media.max-image-size:20MB}") DataSize maxImageSize,
            @Value("${app.media.max-video-size:100MB}") DataSize maxVideoSize,
            @Value("${app.media.max-document-size:50MB}") DataSize maxDocumentSize,
            @Value("${app.media.image-max-dimension:2048}") int imageMaxDimension,
            @Value("${app.media.image-quality:0.85}") float imageQuality,
            @Value("${app.media.ffmpeg-path:ffmpeg}") String ffmpegPath,
            @Value("${app.media.video-crf:23}") int videoCrf,
            @Value("${app.media.video-preset:medium}") String videoPreset) {
        this(buildClient(endpoint, region, accessKey, secretKey, pathStyle),
                buildPresigner(endpoint, region, accessKey, secretKey, pathStyle),
                new Settings(requireBucket(bucket), publicBaseUrl, signedUrlTtl),
                new MediaTypes.Limits(maxImageSize.toBytes(), maxVideoSize.toBytes(), maxDocumentSize.toBytes()),
                new ImageCompressor(imageMaxDimension, imageQuality),
                new VideoTranscoder(ffmpegPath, videoCrf, videoPreset),
                Path.of(workDir));
        checkBucket();
        log.info("Media storage: bucket '{}' at {} (public files via {})", bucket,
                endpoint.isBlank() ? "AWS " + region : endpoint,
                publicBaseUrl.isBlank() ? "signed links (set APP_MEDIA_S3_PUBLIC_BASE_URL to the CDN)" : publicBaseUrl);
        resumePendingVideos();
    }

    /** For tests: injects the clients and skips the startup bucket check. */
    S3MediaStorageService(S3Client s3, S3Presigner presigner, Settings settings, MediaTypes.Limits limits,
                          ImageCompressor imageCompressor, VideoTranscoder videoTranscoder, Path workDir) {
        this.s3 = s3;
        this.presigner = presigner;
        this.settings = new Settings(settings.bucket(), stripTrailingSlash(settings.publicBaseUrl()), settings.signedUrlTtl());
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
        presigner.close();
        s3.close();
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
            put(PUBLIC_PREFIX + key, MediaTypes.contentTypeOf(key), IMMUTABLE, RequestBody.fromBytes(bytes));
            compressed.ifPresent(c -> log.info("Image {} compressed {} KB -> {} KB", key,
                    original.length / 1024, c.bytes().length / 1024));
            return uploaded(key, bytes.length);
        }
        if (accepted.isVideo() && videoTranscoder.isAvailable()) {
            return storeVideo(file, accepted.extension());
        }
        String key = UUID.randomUUID() + "." + accepted.extension();
        try (InputStream in = file.getInputStream()) {
            put(PUBLIC_PREFIX + key, MediaTypes.contentTypeOf(key), IMMUTABLE, RequestBody.fromInputStream(in, file.getSize()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store upload", e);
        }
        return uploaded(key, file.getSize());
    }

    /**
     * Uploads the original under the final {@code .mp4} key right away (no-cache, so neither
     * browsers nor the CDN keep it), then compresses a local copy in the background.
     */
    private MediaUploadResponse storeVideo(MultipartFile file, String extension) {
        String id = UUID.randomUUID().toString();
        String key = id + ".mp4";
        Path source = pending.resolve(id + "." + extension);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, source, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store upload", e);
        }
        put(PUBLIC_PREFIX + key, MediaTypes.contentTypeOf(source.getFileName().toString()), NO_CACHE,
                RequestBody.fromFile(source));
        queueVideo(source, key);
        return MediaUploadResponse.builder()
                .url(MediaTypes.PUBLIC_PATH + key)
                .contentType("video/mp4")
                .size(file.getSize())
                .build();
    }

    private void queueVideo(Path source, String key) {
        Path target = out.resolve(key);
        videoTranscoder.submit(source, target, compressed -> finishVideo(source, target, key, compressed));
    }

    /**
     * Replaces the original with the compressed MP4, or, when compression did not help, marks an
     * original MP4 as final. Other containers stay no-cache: browsers still play them.
     */
    void finishVideo(Path source, Path target, String key, boolean compressed) {
        try {
            if (compressed) {
                put(PUBLIC_PREFIX + key, "video/mp4", IMMUTABLE, RequestBody.fromFile(target));
                log.info("Video {} compressed {} KB -> {} KB", key, Files.size(source) / 1024, Files.size(target) / 1024);
            } else if (source.getFileName().toString().endsWith(".mp4")) {
                put(PUBLIC_PREFIX + key, "video/mp4", IMMUTABLE, RequestBody.fromFile(source));
            }
            Files.deleteIfExists(target);
            Files.deleteIfExists(source);
        } catch (IOException | RuntimeException e) {
            // The original stays online; the work copy is retried at the next start
            log.warn("Could not finalize video {}: {}", key, e.getMessage());
        }
    }

    /** Re-queues videos whose compression was interrupted by a restart. */
    private void resumePendingVideos() {
        if (!videoTranscoder.isAvailable()) return;
        try (Stream<Path> files = Files.list(pending)) {
            files.filter(Files::isRegularFile).forEach(source -> {
                String name = source.getFileName().toString();
                String id = name.substring(0, name.indexOf('.'));
                log.info("Resuming compression of video {}", id);
                queueVideo(source, id + ".mp4");
            });
        } catch (IOException e) {
            log.warn("Could not scan pending videos: {}", e.getMessage());
        }
    }

    @Override
    public Optional<String> publicUrl(String key) {
        requirePublicKey(key);
        if (!settings.publicBaseUrl().isBlank()) {
            return Optional.of(settings.publicBaseUrl() + "/" + PUBLIC_PREFIX + key);
        }
        // No CDN configured (e.g. local MinIO): hand out a signed link to the private bucket
        return Optional.of(presign(PUBLIC_PREFIX + key, MediaTypes.contentTypeOf(key), "inline"));
    }

    /** Only used when a caller asks for the bytes; browsers are redirected via {@link #publicUrl}. */
    @Override
    public Resource load(String key) {
        requirePublicKey(key);
        String objectKey = PUBLIC_PREFIX + key;
        return new ObjectResource(objectKey, head(objectKey).orElseThrow(() -> new ResourceNotFoundException("Media", "key", key)));
    }

    @Override
    public String contentTypeOf(String key) {
        return MediaTypes.contentTypeOf(key);
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
            s3.deleteObject(DeleteObjectRequest.builder().bucket(settings.bucket()).key(PUBLIC_PREFIX + key).build());
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
            // Photos in chat are compressed like public ones
            byte[] original = readAll(file);
            Optional<ImageCompressor.Result> compressed = imageCompressor.compress(original, accepted.extension());
            String key = UUID.randomUUID() + "." + compressed.map(ImageCompressor.Result::extension).orElse(accepted.extension());
            put(privateObjectKey(namespace, key), MediaTypes.contentTypeOf(key), null,
                    RequestBody.fromBytes(compressed.map(ImageCompressor.Result::bytes).orElse(original)));
            return key;
        }
        String key = UUID.randomUUID() + "." + accepted.extension();
        try (InputStream in = file.getInputStream()) {
            put(privateObjectKey(namespace, key), MediaTypes.contentTypeOf(key), null,
                    RequestBody.fromInputStream(in, file.getSize()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store attachment", e);
        }
        return key;
    }

    @Override
    public Resource loadPrivate(String namespace, String key) {
        MediaTypes.requireNamespace(namespace);
        if (key == null || !MediaTypes.PRIVATE_KEY_PATTERN.matcher(key).matches()) {
            throw new ResourceNotFoundException("Attachment", "key", key);
        }
        String objectKey = privateObjectKey(namespace, key);
        return new ObjectResource(objectKey, head(objectKey).orElseThrow(() -> new ResourceNotFoundException("Attachment", "key", key)));
    }

    @Override
    public Optional<String> privateUrl(String namespace, String key) {
        MediaTypes.requireNamespace(namespace);
        if (key == null || !MediaTypes.PRIVATE_KEY_PATTERN.matcher(key).matches()) {
            throw new ResourceNotFoundException("Attachment", "key", key);
        }
        String contentType = MediaTypes.contentTypeOf(key);
        // Images and PDFs open in the browser; drawings, ZIPs and office files download
        boolean viewable = contentType.startsWith("image/") || "application/pdf".equals(contentType);
        return Optional.of(presign(privateObjectKey(namespace, key), contentType, viewable ? "inline" : "attachment"));
    }

    // ---- S3 plumbing ----

    private void put(String objectKey, String contentType, String cacheControl, RequestBody body) {
        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(settings.bucket())
                .key(objectKey)
                .contentType(contentType);
        if (cacheControl != null) request.cacheControl(cacheControl);
        s3.putObject(request.build(), body);
    }

    private Optional<HeadObjectResponse> head(String objectKey) {
        try {
            return Optional.of(s3.headObject(HeadObjectRequest.builder().bucket(settings.bucket()).key(objectKey).build()));
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return Optional.empty();
            throw e;
        }
    }

    boolean exists(String objectKey) {
        return head(objectKey).isPresent();
    }

    /** Raw upload used by the one-time migration from local disk. */
    void putFile(String objectKey, Path file, String cacheControl) {
        put(objectKey, MediaTypes.contentTypeOf(file.getFileName().toString()), cacheControl, RequestBody.fromFile(file));
    }

    private String presign(String objectKey, String contentType, String disposition) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(settings.bucket())
                .key(objectKey)
                .responseContentType(contentType)
                .responseContentDisposition(disposition)
                .build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(settings.signedUrlTtl())
                        .getObjectRequest(get)
                        .build())
                .url()
                .toString();
    }

    private void checkBucket() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(settings.bucket()).build());
        } catch (RuntimeException e) {
            throw new IllegalStateException("Media bucket '" + settings.bucket() + "' is not reachable with the configured "
                    + "endpoint and keys (APP_MEDIA_S3_*): " + e.getMessage(), e);
        }
    }

    static String privateObjectKey(String namespace, String key) {
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

    private static String requireBucket(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("app.media.storage=s3 needs APP_MEDIA_S3_BUCKET");
        }
        return bucket;
    }

    private static String stripTrailingSlash(String url) {
        String value = url == null ? "" : url.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static AwsCredentialsProvider credentials(String accessKey, String secretKey) {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            // e.g. an instance role on AWS; Alibaba deployments set the keys explicitly
            return DefaultCredentialsProvider.builder().build();
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    private static S3Configuration serviceConfiguration(boolean pathStyle) {
        return S3Configuration.builder()
                // MinIO needs path-style URLs; Alibaba OSS only accepts virtual-hosted ones
                .pathStyleAccessEnabled(pathStyle)
                // OSS rejects aws-chunked uploads
                .chunkedEncodingEnabled(false)
                .build();
    }

    private static S3Client buildClient(String endpoint, String region, String accessKey, String secretKey, boolean pathStyle) {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials(accessKey, secretKey))
                .httpClient(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(10))
                        .socketTimeout(Duration.ofMinutes(2))
                        .build())
                .serviceConfiguration(serviceConfiguration(pathStyle))
                // Newer SDKs add CRC checksums that S3-compatible providers do not all accept
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
        return builder.build();
    }

    private static S3Presigner buildPresigner(String endpoint, String region, String accessKey, String secretKey, boolean pathStyle) {
        var builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials(accessKey, secretKey))
                .serviceConfiguration(serviceConfiguration(pathStyle));
        if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
        return builder.build();
    }

    /** Streams an object on demand. */
    private final class ObjectResource extends AbstractResource {
        private final String objectKey;
        private final HeadObjectResponse head;

        ObjectResource(String objectKey, HeadObjectResponse head) {
            this.objectKey = objectKey;
            this.head = head;
        }

        @Override
        public InputStream getInputStream() {
            return s3.getObject(GetObjectRequest.builder().bucket(settings.bucket()).key(objectKey).build());
        }

        @Override
        public long contentLength() {
            return head.contentLength();
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public String getFilename() {
            return objectKey.substring(objectKey.lastIndexOf('/') + 1);
        }

        @Override
        public String getDescription() {
            return "s3://" + settings.bucket() + "/" + objectKey;
        }
    }
}
