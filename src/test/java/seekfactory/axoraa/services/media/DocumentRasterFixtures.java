package seekfactory.axoraa.services.media;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;
import java.util.stream.Stream;

/** Test scans; WebP/AVIF exercise container signatures without requiring native codecs. */
public final class DocumentRasterFixtures {
    public record Scan(String extension, String contentType, byte[] bytes) {}
    private DocumentRasterFixtures() {}

    public static Stream<Scan> scans() throws IOException {
        BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(7);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, random.nextInt());
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ByteArrayOutputStream jpg = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        ImageIO.write(image, "jpg", jpg);
        return Stream.of(new Scan("png", "image/png", png.toByteArray()),
                new Scan("jpg", "image/jpeg", jpg.toByteArray()),
                new Scan("jpeg", "image/jpeg", jpg.toByteArray()),
                new Scan("gif", "image/gif", Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7")),
                new Scan("webp", "image/webp", webp("VP8L")),
                new Scan("avif", "image/avif", avif("mif1", "avif")));
    }

    public static byte[] webp(String chunk) {
        return ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(18)
                .put("WEBP".getBytes(StandardCharsets.US_ASCII))
                .put(chunk.getBytes(StandardCharsets.US_ASCII)).putInt(5)
                .put(new byte[]{0x2f, 0, 0, 0, 0, 0}).array();
    }

    public static byte[] avif(String majorBrand, String compatibleBrand) {
        return ByteBuffer.allocate(28)
                .putInt(20).put("ftyp".getBytes(StandardCharsets.US_ASCII))
                .put(majorBrand.getBytes(StandardCharsets.US_ASCII)).putInt(0)
                .put(compatibleBrand.getBytes(StandardCharsets.US_ASCII))
                .putInt(8).put("mdat".getBytes(StandardCharsets.US_ASCII)).array();
    }
}
