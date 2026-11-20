package seekfactory.axoraa.services.media;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import seekfactory.axoraa.services.services.MediaStorageService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Gives seek videos an adaptive-streaming (HLS) copy and records it on the seek ({@code reels.hls_url}).
 *
 * A periodic scan covers both new uploads (once their compression has finished) and videos stored earlier,
 * a few per run and one at a time on its own thread, so it never holds up other scheduled work or competes
 * with an upload for the single CPU. A video whose packaging fails is skipped until the next restart; the
 * seek keeps playing its plain file either way. Off unless {@code APP_MEDIA_HLS_ENABLED=true}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.media.hls.enabled", havingValue = "true")
public class HlsPublisher {

    static final int VIDEOS_PER_RUN = 3;
    static final String SEGMENT_CACHE = "public, max-age=31536000, immutable";
    static final String PLAYLIST_CACHE = "public, max-age=3600";

    private final JdbcTemplate jdbcTemplate;
    private final MediaStorageService storage;
    private final VideoTranscoder transcoder;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hls-publisher");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean();
    private final Set<String> failed = ConcurrentHashMap.newKeySet();

    public HlsPublisher(JdbcTemplate jdbcTemplate, MediaStorageService storage,
                        @Value("${app.media.ffmpeg-path:ffmpeg}") String ffmpegPath,
                        @Value("${app.media.video-crf:23}") int videoCrf,
                        @Value("${app.media.video-preset:veryfast}") String videoPreset) {
        this(jdbcTemplate, storage, new VideoTranscoder(ffmpegPath, videoCrf, videoPreset));
    }

    HlsPublisher(JdbcTemplate jdbcTemplate, MediaStorageService storage, VideoTranscoder transcoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.storage = storage;
        this.transcoder = transcoder;
        if (!transcoder.isAvailable()) log.warn("Adaptive streaming is enabled but ffmpeg is not available");
    }

    @Scheduled(initialDelayString = "${app.media.hls.initial-delay:90000}", fixedDelayString = "${app.media.hls.scan-interval:120000}")
    public void schedule() {
        if (!transcoder.isAvailable() || !running.compareAndSet(false, true)) return;
        worker.execute(() -> {
            try {
                publishPending();
            } catch (RuntimeException e) {
                log.warn("Adaptive-streaming scan stopped: {}", e.getMessage());
            } finally {
                running.set(false);
            }
        });
    }

    /** One scan: links ready copies to their seeks and packages up to {@link #VIDEOS_PER_RUN} new ones. */
    void publishPending() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, video_url, hls_url FROM reels WHERE video_url LIKE '/api/v1/media/%' ORDER BY created_at DESC");
        int packaged = 0;
        for (Map<String, Object> row : rows) {
            String videoUrl = (String) row.get("video_url");
            Optional<String> id = HlsPaths.videoId(videoUrl);
            Optional<String> key = HlsPaths.videoKey(videoUrl);
            if (id.isEmpty() || key.isEmpty()) continue;
            String master = HlsPaths.masterUrl(id.get());
            if (master.equals(row.get("hls_url"))) continue;
            // A new upload is still being compressed, or this one already failed since the last start
            if (storage.isProcessing(key.get()) || failed.contains(key.get())) continue;

            if (!storage.publicFileExists(HlsPaths.path(id.get(), HlsPaths.MASTER))) {
                if (packaged >= VIDEOS_PER_RUN) continue;
                packaged++;
                if (!packageVideo(id.get(), key.get())) {
                    failed.add(key.get());
                    continue;
                }
            }
            // Only if the seek still shows this video (it may have been replaced meanwhile)
            jdbcTemplate.update("UPDATE reels SET hls_url = ? WHERE id = ? AND video_url = ?", master, row.get("id"), videoUrl);
        }
    }

    boolean packageVideo(String id, String key) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("hls-");
            Path source = dir.resolve("source" + key.substring(key.lastIndexOf('.')));
            Path out = dir.resolve("out");
            storage.downloadPublic(key, source);
            long started = System.currentTimeMillis();
            if (!transcoder.packageHls(source, out)) {
                log.warn("Could not package {} for streaming", key);
                return false;
            }
            upload(id, out);
            log.info("Packaged {} for adaptive streaming in {} s", key, (System.currentTimeMillis() - started) / 1000);
            return true;
        } catch (IOException | RuntimeException e) {
            log.warn("Could not package {} for streaming: {}", key, e.getMessage());
            return false;
        } finally {
            deleteQuietly(dir);
        }
    }

    /** Segments first, then the quality playlists, the master playlist last: a player never finds a gap. */
    private void upload(String id, Path out) throws IOException {
        List<Path> files;
        try (Stream<Path> listing = Files.list(out)) {
            files = listing.filter(Files::isRegularFile)
                    .filter(f -> HlsPaths.isValid(id, f.getFileName().toString()))
                    .sorted(Comparator.comparingInt(HlsPublisher::uploadOrder).thenComparing(Path::toString))
                    .toList();
        }
        for (Path file : files) {
            String name = file.getFileName().toString();
            storage.storePublicFile(HlsPaths.path(id, name), file, HlsPaths.contentTypeOf(name),
                    HlsPaths.isImmutable(name) ? SEGMENT_CACHE : PLAYLIST_CACHE);
        }
    }

    static int uploadOrder(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".ts")) return 0;
        return HlsPaths.MASTER.equals(name) ? 2 : 1;
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(f -> {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException ignored) {
                    // temp files: best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
        transcoder.close();
    }
}
