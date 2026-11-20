package seekfactory.axoraa.services.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real encode of a generated clip. Runs only where ffmpeg is installed
 * (FFMPEG_PATH env var, or `ffmpeg` on the PATH); skipped elsewhere.
 */
class VideoTranscoderTest {

    private static final String FFMPEG = System.getenv().getOrDefault("FFMPEG_PATH", "ffmpeg");

    @Test
    void missingFfmpegIsReportedAndKeepsOriginal(@TempDir Path dir) throws Exception {
        try (VideoTranscoder transcoder = new VideoTranscoder(dir.resolve("no-ffmpeg").toString(), 23, "medium")) {
            assertThat(transcoder.isAvailable()).isFalse();
            Path source = Files.writeString(dir.resolve("a.mp4"), "x");
            assertThat(transcoder.transcode(source, dir.resolve("b.mp4"))).isFalse();
            assertThat(dir.resolve("b.mp4")).doesNotExist();
        }
    }

    @Test
    void bulkyVideoIsShrunkToPlayableH264(@TempDir Path dir) throws Exception {
        try (VideoTranscoder transcoder = new VideoTranscoder(FFMPEG, 23, "veryfast")) {
            assumeTrue(transcoder.isAvailable(), "ffmpeg not installed");

            // 4 s of 2560x1440 60 fps test pattern + tone, encoded nearly losslessly (like a phone upload)
            Path source = dir.resolve("upload.mp4");
            Process gen = new ProcessBuilder(List.of(FFMPEG, "-hide_banner", "-loglevel", "error", "-y",
                    "-f", "lavfi", "-i", "testsrc2=size=2560x1440:rate=60:duration=4",
                    "-f", "lavfi", "-i", "sine=frequency=440:duration=4",
                    "-c:v", "libx264", "-preset", "ultrafast", "-crf", "8", "-c:a", "aac", "-b:a", "320k",
                    source.toString())).redirectErrorStream(true).start();
            gen.getInputStream().readAllBytes();
            assertThat(gen.waitFor(120, TimeUnit.SECONDS)).isTrue();

            Path target = dir.resolve("final.mp4");
            assertThat(transcoder.transcode(source, target)).isTrue();

            assertThat(Files.size(target)).isLessThan(Files.size(source) / 2);
            String probe = probe(target);
            assertThat(probe).contains("h264").contains("1920x1080").contains("aac");
            assertThat(dir.resolve("final.mp4.part.mp4")).doesNotExist();
        }
    }

    @Test
    void portraitVideoIsPackagedInTwoStreamingQualities(@TempDir Path dir) throws Exception {
        try (VideoTranscoder transcoder = new VideoTranscoder(FFMPEG, 23, "veryfast")) {
            assumeTrue(transcoder.isAvailable(), "ffmpeg not installed");

            // 10 s portrait clip (like a phone seek) with sound
            Path source = dir.resolve("seek.mp4");
            Process gen = new ProcessBuilder(List.of(FFMPEG, "-hide_banner", "-loglevel", "error", "-y",
                    "-f", "lavfi", "-i", "testsrc2=size=1080x1920:rate=30:duration=10",
                    "-f", "lavfi", "-i", "sine=frequency=440:duration=10",
                    "-c:v", "libx264", "-preset", "ultrafast", "-c:a", "aac", source.toString()))
                    .redirectErrorStream(true).start();
            gen.getInputStream().readAllBytes();
            assertThat(gen.waitFor(120, TimeUnit.SECONDS)).isTrue();

            Path out = dir.resolve("hls");
            assertThat(transcoder.packageHls(source, out)).isTrue();

            String master = Files.readString(out.resolve("master.m3u8"));
            assertThat(master).contains("stream_0.m3u8").contains("stream_1.m3u8").contains("RESOLUTION=360x640").contains("RESOLUTION=720x1280");
            String small = Files.readString(out.resolve("stream_0.m3u8"));
            assertThat(small).contains("#EXT-X-PLAYLIST-TYPE:VOD").contains("stream_0_000.ts").contains("#EXT-X-ENDLIST");
            try (var files = Files.list(out)) {
                assertThat(files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".ts")).count()).isGreaterThanOrEqualTo(4);
            }
            assertThat(probe(out.resolve("stream_0_000.ts"))).contains("h264").contains("aac");
        }
    }

    @Test
    void silentVideoIsPackagedToo(@TempDir Path dir) throws Exception {
        try (VideoTranscoder transcoder = new VideoTranscoder(FFMPEG, 23, "veryfast")) {
            assumeTrue(transcoder.isAvailable(), "ffmpeg not installed");
            Path source = dir.resolve("silent.mp4");
            Process gen = new ProcessBuilder(List.of(FFMPEG, "-hide_banner", "-loglevel", "error", "-y",
                    "-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=30:duration=5",
                    "-c:v", "libx264", "-preset", "ultrafast", source.toString()))
                    .redirectErrorStream(true).start();
            gen.getInputStream().readAllBytes();
            assertThat(gen.waitFor(120, TimeUnit.SECONDS)).isTrue();

            assertThat(transcoder.packageHls(source, dir.resolve("hls"))).isTrue();
            assertThat(Files.readString(dir.resolve("hls").resolve("master.m3u8"))).contains("RESOLUTION=640x360");
        }
    }

    /** Stream summary via `ffmpeg -i` (ffprobe may not be installed next to ffmpeg). */
    private static String probe(Path file) throws Exception {
        Process p = new ProcessBuilder(FFMPEG, "-hide_banner", "-i", file.toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor(30, TimeUnit.SECONDS);
        return out;
    }
}
