package seekfactory.axoraa.services.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class Mp4LayoutTest {

    @TempDir
    Path dir;

    private static byte[] box(String type, byte[] body) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        data.writeInt(8 + body.length);
        data.write(type.getBytes(StandardCharsets.US_ASCII));
        data.write(body);
        return out.toByteArray();
    }

    /** moov containing an mvhd (version 0) with the given timescale and duration. */
    private static byte[] moov(int timescale, int duration) throws IOException {
        ByteArrayOutputStream mvhd = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(mvhd);
        data.writeInt(0);          // version 0 + flags
        data.writeInt(0);          // creation time
        data.writeInt(0);          // modification time
        data.writeInt(timescale);
        data.writeInt(duration);
        data.write(new byte[80]);  // rest of mvhd
        return box("moov", box("mvhd", mvhd.toByteArray()));
    }

    private Path file(byte[]... boxes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] b : boxes) out.write(b);
        Path path = dir.resolve("v.mp4");
        Files.write(path, out.toByteArray());
        return path;
    }

    @Test
    void indexBeforeMediaStartsEarly() throws IOException {
        Mp4Layout layout = Mp4Layout.read(file(box("ftyp", new byte[8]), moov(1000, 15_500), box("mdat", new byte[64])));
        assertThat(layout.indexFirst()).isTrue();
        assertThat(layout.durationSeconds()).isCloseTo(15.5, within(0.001));
    }

    @Test
    void indexAfterMediaIsReported() throws IOException {
        Mp4Layout layout = Mp4Layout.read(file(box("ftyp", new byte[8]), box("mdat", new byte[64]), moov(600, 1200)));
        assertThat(layout.indexFirst()).isFalse();
        assertThat(layout.durationSeconds()).isCloseTo(2.0, within(0.001));
    }

    @Test
    void garbageDoesNotLoopOrThrow() throws IOException {
        Path path = dir.resolve("bad.mp4");
        Files.write(path, new byte[]{0, 0, 0, 2, 'x', 'x', 'x', 'x', 1, 2, 3});
        assertThat(Mp4Layout.read(path).indexFirst()).isFalse();
    }
}
