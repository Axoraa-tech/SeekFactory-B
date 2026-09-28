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

    /** Stream summary via `ffmpeg -i` (ffprobe may not be installed next to ffmpeg). */
    private static String probe(Path file) throws Exception {
        Process p = new ProcessBuilder(FFMPEG, "-hide_banner", "-i", file.toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor(30, TimeUnit.SECONDS);
        return out;
    }
}
