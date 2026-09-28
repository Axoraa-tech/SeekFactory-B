package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import seekfactory.axoraa.dto.Response.media.MediaUploadResponse;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalMediaStorageServiceTest {

    private static final String FFMPEG = System.getenv().getOrDefault("FFMPEG_PATH", "ffmpeg");

    private static LocalMediaStorageService service(Path dir, String ffmpeg) {
        return new LocalMediaStorageService(dir.toString(), DataSize.ofMegabytes(20), DataSize.ofMegabytes(100),
                DataSize.ofMegabytes(50), 2048, 0.85f, ffmpeg, 23, "veryfast");
    }

    private static String key(MediaUploadResponse response) {
        return response.getUrl().substring(LocalMediaStorageService.PUBLIC_PATH.length());
    }

    @Test
    void photoUploadIsStoredCompressed(@TempDir Path dir) throws Exception {
        BufferedImage img = new BufferedImage(3000, 2000, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(7);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) img.setRGB(x, y, random.nextInt(0x303030) + 0x404040);
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(img, "png", png);
        LocalMediaStorageService storage = service(dir, dir.resolve("no-ffmpeg").toString());

        MediaUploadResponse res = storage.store(
                new MockMultipartFile("file", "shot.png", "image/png", png.toByteArray()), "image");

        assertThat(res.getUrl()).endsWith(".jpg");
        assertThat(res.getContentType()).isEqualTo("image/jpeg");
        assertThat(res.getSize()).isLessThan(png.size());
        assertThat(Files.size(dir.resolve(key(res)))).isEqualTo(res.getSize());
    }

    @Test
    void withoutFfmpegVideosAreStoredAsUploaded(@TempDir Path dir) {
        LocalMediaStorageService storage = service(dir, dir.resolve("no-ffmpeg").toString());

        MediaUploadResponse res = storage.store(
                new MockMultipartFile("file", "clip.webm", "video/webm", new byte[]{1, 2, 3}), "video");

        assertThat(res.getUrl()).endsWith(".webm");
        assertThat(storage.isProcessing(key(res))).isFalse();
        assertThat(storage.load(key(res)).exists()).isTrue();
    }

    @Test
    void videoServesOriginalWhileProcessingThenTheCompressedFile(@TempDir Path dir) throws Exception {
        LocalMediaStorageService storage = service(dir, FFMPEG);
        try {
            Path clip = dir.resolve("clip.mov");
            Process gen = new ProcessBuilder(List.of(FFMPEG, "-hide_banner", "-loglevel", "error", "-y",
                    "-f", "lavfi", "-i", "testsrc2=size=1920x1080:rate=30:duration=3",
                    "-c:v", "libx264", "-preset", "ultrafast", "-crf", "6", clip.toString()))
                    .redirectErrorStream(true).start();
            gen.getInputStream().readAllBytes();
            assumeTrue(gen.waitFor(60, TimeUnit.SECONDS) && gen.exitValue() == 0, "ffmpeg not installed");

            MediaUploadResponse res = storage.store(
                    new MockMultipartFile("file", "clip.mov", "video/quicktime", Files.readAllBytes(clip)), "video");
            String key = key(res);

            assertThat(key).endsWith(".mp4");
            // Immediately playable: the original is served under the final key, uncached
            assertThat(storage.load(key).exists()).isTrue();

            long deadline = System.currentTimeMillis() + 60_000;
            while (storage.isProcessing(key) && System.currentTimeMillis() < deadline) Thread.sleep(200);

            assertThat(storage.isProcessing(key)).isFalse();
            assertThat(storage.contentTypeOf(key)).isEqualTo("video/mp4");
            assertThat(Files.size(dir.resolve(key))).isLessThan(Files.size(clip));
            try (var pending = Files.list(dir.resolve("pending"))) {
                assertThat(pending).isEmpty();
            }
        } finally {
            storage.shutdown();
        }
    }
}
