package seekfactory.axoraa.services.media;

import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ImageCompressorTest {

    private final ImageCompressor compressor = new ImageCompressor(2048, 0.85f);

    /** Photo-like content: gradient plus fine noise, so it compresses like a real camera image. */
    private static BufferedImage photo(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = (x * 255 / w + random.nextInt(24)) & 0xFF;
                int g = (y * 255 / h + random.nextInt(24)) & 0xFF;
                int b = (128 + random.nextInt(24)) & 0xFF;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    private static byte[] jpeg(BufferedImage img, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static BufferedImage read(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void largePhotoIsResizedToMaxDimensionAndShrinks() throws IOException {
        byte[] original = jpeg(photo(4000, 3000), 0.97f);

        Optional<ImageCompressor.Result> result = compressor.compress(original, "jpg");

        assertThat(result).isPresent();
        assertThat(result.get().extension()).isEqualTo("jpg");
        assertThat(result.get().bytes().length).isLessThan(original.length / 3);
        BufferedImage out = read(result.get().bytes());
        assertThat(out.getWidth()).isEqualTo(2048);
        assertThat(out.getHeight()).isEqualTo(1536);
    }

    @Test
    void opaquePngPhotoBecomesJpeg() throws IOException {
        byte[] original = png(photo(1200, 800));

        Optional<ImageCompressor.Result> result = compressor.compress(original, "png");

        assertThat(result).isPresent();
        assertThat(result.get().extension()).isEqualTo("jpg");
        assertThat(read(result.get().bytes()).getWidth()).isEqualTo(1200); // never upscaled or cropped
    }

    @Test
    void transparentPngStaysPng() throws IOException {
        // Detailed cut-out product shot: photo content inside, transparent corners
        BufferedImage cutout = new BufferedImage(3000, 1000, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = cutout.createGraphics();
        g.setClip(new java.awt.geom.Ellipse2D.Double(200, 100, 2600, 800));
        g.drawImage(photo(3000, 1000), 0, 0, null);
        g.dispose();

        Optional<ImageCompressor.Result> result = compressor.compress(png(cutout), "png");

        assertThat(result).isPresent();
        assertThat(result.get().extension()).isEqualTo("png");
        BufferedImage out = read(result.get().bytes());
        assertThat(out.getWidth()).isEqualTo(2048);
        assertThat(out.getColorModel().hasAlpha()).isTrue();
        assertThat(out.getRGB(0, 0) >>> 24).isZero();
    }

    @Test
    void simpleLogoPngThatWouldGrowIsKept() throws IOException {
        BufferedImage logo = new BufferedImage(3000, 1000, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = logo.createGraphics();
        g.setColor(new Color(26, 115, 232));
        g.fillOval(200, 100, 2600, 800);
        g.dispose();

        // Flat colours are already tiny as PNG; a resized, antialiased copy would be larger
        assertThat(compressor.compress(png(logo), "png")).isEmpty();
    }

    @Test
    void alreadySmallJpegIsKept() throws IOException {
        byte[] original = jpeg(photo(400, 300), 0.6f);

        assertThat(compressor.compress(original, "jpg")).isEmpty();
    }

    @Test
    void gifWebpAndBrokenFilesAreLeftAlone() {
        assertThat(compressor.compress(new byte[]{1, 2, 3}, "gif")).isEmpty();
        assertThat(compressor.compress(new byte[]{1, 2, 3}, "webp")).isEmpty();
        assertThat(compressor.compress(new byte[]{1, 2, 3}, "jpg")).isEmpty();
    }

    @Test
    void exifRotationIsAppliedToPixels() {
        // 4x2 image, red top-left pixel
        BufferedImage img = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xFF0000);

        BufferedImage cw = ImageCompressor.applyOrientation(img, 6);   // rotate 90 clockwise
        assertThat(cw.getWidth()).isEqualTo(2);
        assertThat(cw.getHeight()).isEqualTo(4);
        assertThat(cw.getRGB(1, 0) & 0xFFFFFF).isEqualTo(0xFF0000);  // top-left moves to top-right

        BufferedImage ccw = ImageCompressor.applyOrientation(img, 8);  // rotate 90 counter-clockwise
        assertThat(ccw.getRGB(0, 3) & 0xFFFFFF).isEqualTo(0xFF0000); // top-left moves to bottom-left

        BufferedImage upside = ImageCompressor.applyOrientation(img, 3);
        assertThat(upside.getRGB(3, 1) & 0xFFFFFF).isEqualTo(0xFF0000);

        assertThat(ImageCompressor.applyOrientation(img, 1)).isSameAs(img);
    }
}
