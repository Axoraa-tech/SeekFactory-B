package seekfactory.axoraa.services.media;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import seekfactory.axoraa.services.services.MediaStorageService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-off background job for seek videos already stored (e.g. imported before uploads were
 * compressed): videos whose index sits at the end (playback waits for a large part of the
 * download) get it moved to the front, losslessly; videos far above the upload bitrate are
 * re-encoded with the same settings new uploads get. Each file keeps its key, so no link changes.
 * Off by default; turn on with APP_MEDIA_OPTIMIZE_VIDEOS=true for one start.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.media.optimize-videos", havingValue = "true")
public class VideoOptimizer implements ApplicationRunner {

    /** Above this the video is re-encoded (new uploads land at ~1-4 Mbps). */
    static final long REENCODE_ABOVE_BITS_PER_SECOND = 5_000_000;
    private static final Pattern KEY = Pattern.compile(
            "/api/v1/media/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.mp4)$");

    private final JdbcTemplate jdbcTemplate;
    private final MediaStorageService storage;
    private final VideoTranscoder transcoder;

    public VideoOptimizer(JdbcTemplate jdbcTemplate, MediaStorageService storage,
                          @Value("${app.media.ffmpeg-path:ffmpeg}") String ffmpegPath,
                          @Value("${app.media.video-crf:23}") int videoCrf,
                          @Value("${app.media.video-preset:medium}") String videoPreset) {
        this.jdbcTemplate = jdbcTemplate;
        this.storage = storage;
        this.transcoder = new VideoTranscoder(ffmpegPath, videoCrf, videoPreset);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!transcoder.isAvailable()) {
            log.warn("Video optimisation skipped: ffmpeg is not available");
            return;
        }
        Thread.ofVirtual().name("video-optimizer").start(this::optimizeAll);
    }

    private void optimizeAll() {
        Set<String> keys = new LinkedHashSet<>();
        jdbcTemplate.query("SELECT video_url FROM reels", rs -> {
            String url = rs.getString(1);
            Matcher m = url == null ? null : KEY.matcher(url);
            if (m != null && m.find()) keys.add(m.group(1));
        });
        log.info("Checking {} seek videos", keys.size());
        int moved = 0, reencoded = 0, fine = 0, failed = 0;
        for (String key : keys) {
            if (storage.isProcessing(key)) continue;  // a new upload still being compressed
            switch (optimize(key)) {
                case MOVED -> moved++;
                case REENCODED -> reencoded++;
                case FINE -> fine++;
                case FAILED -> failed++;
            }
        }
        log.info("Seek videos: {} index moved to the front, {} re-encoded, {} already fine, {} failed",
                moved, reencoded, fine, failed);
    }

    enum Outcome { MOVED, REENCODED, FINE, FAILED }

    Outcome optimize(String key) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("video-optimize");
            Path original = dir.resolve("original.mp4");
            Path result = dir.resolve("result.mp4");
            storage.downloadPublic(key, original);
            Mp4Layout layout = Mp4Layout.read(original);
            long size = Files.size(original);
            long bitsPerSecond = layout.durationSeconds() > 0 ? (long) (size * 8 / layout.durationSeconds()) : 0;

            if (bitsPerSecond > REENCODE_ABOVE_BITS_PER_SECOND && transcoder.transcode(original, result)) {
                storage.replacePublic(key, result);
                log.info("Re-encoded {} ({} MB, {} kbps)", key, size / 1_048_576, bitsPerSecond / 1000);
                return Outcome.REENCODED;
            }
            if (!layout.indexFirst()) {
                if (!transcoder.moveIndexToFront(original, result)) return Outcome.FAILED;
                storage.replacePublic(key, result);
                log.info("Moved the index of {} ({} MB) to the front", key, size / 1_048_576);
                return Outcome.MOVED;
            }
            return Outcome.FINE;
        } catch (IOException | RuntimeException e) {
            log.warn("Could not optimise {}: {}", key, e.getMessage());
            return Outcome.FAILED;
        } finally {
            deleteQuietly(dir);
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (var files = Files.list(dir)) {
            files.forEach(f -> {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException ignored) {
                    // temp files: best effort
                }
            });
            Files.deleteIfExists(dir);
        } catch (IOException ignored) {
            // best effort
        }
    }
}
