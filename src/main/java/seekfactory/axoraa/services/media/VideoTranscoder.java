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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.RejectedExecutionException;

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
    private final Semaphore slots = new Semaphore(8);
    private final java.util.concurrent.atomic.AtomicLong reservedDisk = new java.util.concurrent.atomic.AtomicLong();
    private final ExecutorService worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8), r -> {
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
        try (Admission admission = reserve()) {
            return submit(admission, source, target, onDone);
        }
    }

    /** Reserve before writing a work copy: a full queue must not fill the disk. */
    public Admission reserve() {
        if (!slots.tryAcquire()) throw new RejectedExecutionException("Video processing is busy; retry later");
        return new Admission();
    }

    /** Keep headroom for inputs, encoded outputs and ffmpeg's temporary file across all admitted jobs. */
    public synchronized Admission reserve(Path workDirectory, long inputBytes) throws IOException {
        Admission admission = reserve();
        long required = Math.multiplyExact(inputBytes, 3);
        try {
            if (Files.getFileStore(workDirectory).getUsableSpace() < reservedDisk.get() + required + 256L*1024*1024)
                throw new RejectedExecutionException("Insufficient video work space");
            admission.diskBytes = required;
            reservedDisk.addAndGet(required);
            return admission;
        } catch (IOException | RuntimeException failure) { admission.close(); throw failure; }
    }

    public final class Admission implements AutoCloseable {
        private boolean transferred;
        private boolean closed;
        private long diskBytes;
        private Admission() {}
        private void release() { reservedDisk.addAndGet(-diskBytes); slots.release(); }
        @Override public synchronized void close() {
            if (!closed && !transferred) release();
            closed = true;
        }
    }

    public Future<?> submit(Admission admission, Path source, Path target,
                            java.util.function.Consumer<Boolean> onDone) {
        synchronized (admission) {
            if (admission.closed || admission.transferred) throw new IllegalStateException("Admission already used");
            Future<?> future = worker.submit(() -> {
                try { onDone.accept(transcode(source, target)); }
                finally { admission.release(); }
            });
            admission.transferred = true;
            return future;
        }
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

    /**
     * Lossless rewrite with the MP4 index ("moov") moved to the front, so playback can start before
     * the whole file has downloaded. Same streams, same quality; true when target holds the result.
     */
    boolean moveIndexToFront(Path source, Path target) {
        if (!available) return false;
        // ffmpeg infers the output container from the last extension. Preserve QuickTime
        // for gallery MOVs instead of silently writing MP4 bytes under a .mov key.
        String extension = target.getFileName().toString().endsWith(".mov") ? ".mov" : ".mp4";
        Path temp = target.resolveSibling(target.getFileName() + ".part" + extension);
        try {
            Process process = new ProcessBuilder(ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                    "-i", source.toString(), "-map", "0", "-c", "copy", "-movflags", "+faststart", temp.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                return false;
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(temp) || Files.size(temp) == 0) return false;
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            log.warn("Could not move the index of {} to the front: {}", source.getFileName(), e.getMessage());
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

    /** Short side scaled to at most {@code size}, keeping the aspect ratio (seeks are often portrait). */
    private static String shortSide(int size) {
        return "scale=w='if(gte(iw,ih),-2,min(" + size + ",iw))':h='if(gte(iw,ih),min(" + size + ",ih),-2)'";
    }

    /** One quality of the streaming ladder. */
    record Rung(int shortSide, String videoBitrate, String maxRate, String bufSize, String audioBitrate) {}

    /**
     * The quality ladder for a source whose shorter side is {@code sourceShortSide} px. The first rung is light
     * on purpose: a phone in mainland China starts on ~0.35 Mbps (a 2-second piece is ~90 KB) and steps up as
     * soon as the link allows. 720p is only made from HD sources (an upscale would only cost bytes).
     */
    static java.util.List<Rung> ladder(int sourceShortSide) {
        java.util.List<Rung> rungs = new java.util.ArrayList<>(java.util.List.of(
                new Rung(240, "300k", "360k", "600k", "64k"),
                new Rung(360, "700k", "820k", "1400k", "96k")));
        if (sourceShortSide >= 600) rungs.add(new Rung(720, "1800k", "2100k", "3600k", "96k"));
        return rungs;
    }

    /** Seconds per segment: short pieces so the first frame needs only a small download. */
    static final int SEGMENT_SECONDS = 2;

    /**
     * Adaptive-streaming copy of a (compressed) video (layout v2, see {@link HlsPaths}): the {@link #ladder}
     * qualities as H.264/AAC in {@value #SEGMENT_SECONDS}-second MPEG-TS segments with aligned keyframes, written
     * to {@code outDir} as {@code index.m3u8}, {@code v2_<n>.m3u8} and {@code v2_<n>_<seq>.ts}. Runs at low CPU
     * priority so the API keeps answering while it works. True when the master and first playlist exist.
     */
    public boolean packageHls(Path source, Path outDir) {
        if (!available) return false;
        try {
            Files.createDirectories(outDir);
            SourceInfo info = probeSource(source);
            java.util.List<Rung> rungs = ladder(info.shortSide());
            int n = rungs.size();

            StringBuilder graph = new StringBuilder("[0:v]split=" + n);
            for (int i = 0; i < n; i++) graph.append("[s").append(i).append(']');
            graph.append(';');
            for (int i = 0; i < n; i++) {
                graph.append("[s").append(i).append(']').append(shortSide(rungs.get(i).shortSide())).append("[v").append(i).append(']');
                if (i < n - 1) graph.append(';');
            }

            java.util.List<String> command = new java.util.ArrayList<>(lowPriority());
            command.addAll(java.util.List.of(ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-i", source.toString(),
                    "-filter_complex", graph.toString()));
            StringBuilder streamMap = new StringBuilder();
            for (int i = 0; i < n; i++) {
                command.addAll(java.util.List.of("-map", "[v" + i + "]"));
                if (info.audio()) command.addAll(java.util.List.of("-map", "0:a:0"));
                if (i > 0) streamMap.append(' ');
                streamMap.append("v:").append(i).append(info.audio() ? ",a:" + i : "");
            }
            command.addAll(java.util.List.of(
                    "-c:v", "libx264", "-preset", preset, "-threads", "1", "-profile:v", "main", "-pix_fmt", "yuv420p",
                    "-fpsmax", "30", "-sc_threshold", "0",
                    "-force_key_frames", "expr:gte(t,n_forced*" + SEGMENT_SECONDS + ")"));
            for (int i = 0; i < n; i++) {
                Rung r = rungs.get(i);
                command.addAll(java.util.List.of("-b:v:" + i, r.videoBitrate(), "-maxrate:v:" + i, r.maxRate(), "-bufsize:v:" + i, r.bufSize()));
            }
            if (info.audio()) {
                command.addAll(java.util.List.of("-c:a", "aac", "-ac", "2"));
                for (int i = 0; i < n; i++) command.addAll(java.util.List.of("-b:a:" + i, rungs.get(i).audioBitrate()));
            }
            command.addAll(java.util.List.of(
                    "-f", "hls", "-hls_time", String.valueOf(SEGMENT_SECONDS), "-hls_playlist_type", "vod",
                    "-hls_flags", "independent_segments",
                    "-hls_segment_filename", outDir.resolve("v2_%v_%03d.ts").toString(),
                    "-master_pl_name", HlsPaths.MASTER,
                    "-var_stream_map", streamMap.toString(),
                    outDir.resolve("v2_%v.m3u8").toString()));

            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0
                    && Files.isRegularFile(outDir.resolve(HlsPaths.MASTER))
                    && Files.isRegularFile(outDir.resolve("v2_0.m3u8"));
        } catch (IOException e) {
            log.warn("Could not package {} for streaming: {}", source.getFileName(), e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Whether the source has sound, and its shorter side in px (0 when unknown). */
    record SourceInfo(boolean audio, int shortSide) {}

    private static final java.util.regex.Pattern VIDEO_SIZE =
            java.util.regex.Pattern.compile("Video:.*?\\b(\\d{2,5})x(\\d{2,5})\\b");

    /** ffmpeg lists the input's streams on stderr when given no output (it then exits with an error). */
    private SourceInfo probeSource(Path source) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(ffmpeg, "-hide_banner", "-i", source.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(p.getInputStream().readAllBytes());
        p.waitFor(60, TimeUnit.SECONDS);
        return parseSourceInfo(output);
    }

    static SourceInfo parseSourceInfo(String ffmpegOutput) {
        java.util.regex.Matcher m = VIDEO_SIZE.matcher(ffmpegOutput);
        int shortSide = m.find() ? Math.min(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))) : 0;
        return new SourceInfo(ffmpegOutput.contains("Audio:"), shortSide);
    }

    /** {@code nice -n 19} when the system has it: encoding yields the CPU to the API's requests. */
    private static java.util.List<String> lowPriority() {
        for (String nice : new String[]{"/usr/bin/nice", "/bin/nice"}) {
            if (Files.isExecutable(Path.of(nice))) return java.util.List.of(nice, "-n", "19");
        }
        return java.util.List.of();
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
