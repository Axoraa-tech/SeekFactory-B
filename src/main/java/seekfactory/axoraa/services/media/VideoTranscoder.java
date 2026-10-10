package seekfactory.axoraa.services.media;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Re-encodes uploaded seek videos with ffmpeg so they take far less storage and bandwidth
 * while looking the same to a viewer.
 *
 * <ul>
 *   <li>H.264 (plays everywhere) at CRF 23: x264's quality-targeted mode, generally regarded
 *       as visually transparent; bitrate is capped at 6 Mbit/s for very noisy footage.</li>
 *   <li>Scaled down (never up) to fit 1920x1080 in either orientation, max 30 fps.</li>
 *   <li>AAC 128 kbit/s stereo audio; metadata (GPS, device) stripped.</li>
 *   <li>{@code +faststart} moves the index to the front so playback starts before download ends.</li>
 * </ul>
 * One video is encoded at a time, single-threaded, so uploads never starve the API of CPU or memory. When ffmpeg is not
 * installed (e.g. a developer laptop) the transcoder reports unavailable and originals are kept.
 */
@Slf4j
public class VideoTranscoder implements AutoCloseable {

    private static final long TIMEOUT_MINUTES = 20;

    private final String ffmpeg;
    private final int crf;
    private final String preset;
    private final boolean available;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "video-transcoder");
        t.setDaemon(true);
        return t;
    });

    public VideoTranscoder(String ffmpegPath, int crf, String preset) {
        this.ffmpeg = ffmpegPath;
        this.crf = crf;
        this.preset = preset;
        this.available = probe(ffmpegPath);
        if (available) {
            log.info("Video compression enabled (ffmpeg: {}, CRF {}, preset {})", ffmpegPath, crf, preset);
        } else {
            log.warn("ffmpeg not found at '{}': uploaded videos are stored uncompressed. "
                    + "Install ffmpeg or set APP_MEDIA_FFMPEG_PATH.", ffmpegPath);
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Queues {@code source} for encoding into {@code target}. {@code onDone} receives true when
     * a smaller file was written to target (atomically), false when the original should stay.
     */
    public Future<?> submit(Path source, Path target, java.util.function.Consumer<Boolean> onDone) {
        return worker.submit(() -> onDone.accept(transcode(source, target)));
    }

    /** Synchronous encode; true when target now holds a smaller, valid MP4. */
    boolean transcode(Path source, Path target) {
        if (!available) return false;
        Path temp = target.resolveSibling(target.getFileName() + ".part.mp4");
        long started = System.nanoTime();
        try {
            List<String> command = new ArrayList<>(List.of(
                    ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                    "-i", source.toString(),
                    "-map", "0:v:0", "-map", "0:a:0?",
                    // One thread and a short lookahead keep ffmpeg to roughly 100-150 MB, so it fits next
                    // to the JVM on a 512 MB instance instead of getting the whole container OOM-killed
                    "-threads", "1",
                    "-c:v", "libx264", "-preset", preset, "-crf", String.valueOf(crf),
                    "-x264-params", "rc-lookahead=10",
                    "-maxrate", "6M", "-bufsize", "12M",
                    "-profile:v", "high", "-pix_fmt", "yuv420p",
                    "-vf", "scale=w='min(iw,if(gte(iw,ih),1920,1080))':h='min(ih,if(gte(iw,ih),1080,1920))'"
                            + ":force_original_aspect_ratio=decrease:force_divisible_by=2",
                    "-fpsmax", "30",
                    "-c:a", "aac", "-b:a", "128k", "-ac", "2",
                    "-map_metadata", "-1",
                    "-movflags", "+faststart",
                    temp.toString()));
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                log.warn("Video compression timed out after {} min: {}", TIMEOUT_MINUTES, source.getFileName());
                return false;
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(temp) || Files.size(temp) == 0) {
                log.warn("Video compression failed (exit {}) for {}", process.exitValue(), source.getFileName());
                return false;
            }
            long before = Files.size(source);
            long after = Files.size(temp);
            if (after >= before) {
                log.info("Video {} already compact ({} KB); keeping original", source.getFileName(), before / 1024);
                return false;
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("Video {} compressed {} KB -> {} KB ({}% smaller) in {} s", target.getFileName(),
                    before / 1024, after / 1024, Math.round(100.0 * (before - after) / before),
                    TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started));
            return true;
        } catch (IOException e) {
            log.warn("Video compression error for {}: {}", source.getFileName(), e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    private static boolean probe(String ffmpegPath) {
        try {
            Process p = new ProcessBuilder(ffmpegPath, "-hide_banner", "-encoders")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(p.getInputStream().readAllBytes());
            return p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0 && output.contains("libx264");
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
