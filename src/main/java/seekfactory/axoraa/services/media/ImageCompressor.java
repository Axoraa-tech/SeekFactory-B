package seekfactory.axoraa.services.media;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

/**
 * Shrinks uploaded photos without a visible quality loss.
 *
 * <ul>
 *   <li>Longest side capped at {@code maxDimension} (2048 px by default): sharp on retina
 *       product pages, while a 12 MP phone photo drops from ~4-8 MB to a few hundred KB.</li>
 *   <li>Opaque images are re-encoded as JPEG at {@code quality} (0.85 by default, widely
 *       considered visually lossless). Images with transparency (logos) stay PNG.</li>
 *   <li>The EXIF orientation is applied to the pixels, because the re-encoded file carries no
 *       metadata (which also drops GPS and camera details from photos).</li>
 *   <li>GIF (possibly animated) and WebP (already efficient; not decodable by ImageIO) are left
 *       as they are.</li>
 * </ul>
 * The caller keeps the original whenever {@link #compress} returns empty or a larger file.
 */
@Slf4j
public class ImageCompressor {

    public record Result(byte[] bytes, String extension) {}

    private final int maxDimension;
    private final float quality;

    public ImageCompressor(int maxDimension, float quality) {
        this.maxDimension = maxDimension;
        this.quality = quality;
    }

    /**
     * @param original  uploaded bytes
     * @param extension allowlisted extension of the upload (png, jpg, gif, webp)
     * @return the compressed image, or empty when it should be stored unchanged
     */
    public Optional<Result> compress(byte[] original, String extension) {
        if (!"jpg".equals(extension) && !"png".equals(extension)) {
            return Optional.empty();
        }
        try {
            BufferedImage image = decode(original);
            if (image == null) return Optional.empty();

            image = applyOrientation(image, readOrientation(original));
            image = downscale(image);

            boolean transparent = image.getColorModel().hasAlpha() && hasTransparentPixel(image);
            Result result = transparent
                    ? new Result(encodePng(image), "png")
                    : new Result(encodeJpeg(toRgb(image)), "jpg");
            return result.bytes().length < original.length ? Optional.of(result) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            // CMYK JPEGs, truncated files, exotic PNGs: store the original rather than fail the upload
            log.warn("Image compression skipped ({}): {}", extension, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Decodes with source subsampling when the image is far larger than the target, so a
     * huge (or decompression-bomb) upload never needs its full pixel buffer in memory.
     */
    private BufferedImage decode(byte[] bytes) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, Math.max(width, height) / (maxDimension * 2));
                if (step > 1) param.setSourceSubsampling(step, step, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
    }

    /** EXIF orientation tag (1 = upright); 1 when absent or unreadable. */
    static int readOrientation(byte[] bytes) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            ExifIFD0Directory exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception ignored) {
            // No or broken metadata: treat as upright
        }
        return 1;
    }

    /** Rotates/flips pixels so the image looks right without its EXIF orientation tag. */
    static BufferedImage applyOrientation(BufferedImage image, int orientation) {
        if (orientation <= 1 || orientation > 8) return image;
        int w = image.getWidth();
        int h = image.getHeight();
        boolean swap = orientation >= 5;
        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.translate(w, 0); t.scale(-1, 1); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.translate(0, h); t.scale(1, -1); }
            case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            default -> { return image; }
        }
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, imageType(image));
        Graphics2D g = out.createGraphics();
        g.drawImage(image, t, null);
        g.dispose();
        return out;
    }

    /** Fits the longest side into maxDimension; halving steps keep downscaled detail crisp. */
    private BufferedImage downscale(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int longest = Math.max(w, h);
        if (longest <= maxDimension) return image;

        double scale = (double) maxDimension / longest;
        int targetW = Math.max(1, (int) Math.round(w * scale));
        int targetH = Math.max(1, (int) Math.round(h * scale));
        BufferedImage current = image;
        while (current.getWidth() / 2 >= targetW && current.getHeight() / 2 >= targetH) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return resize(current, targetW, targetH);
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, imageType(src));
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static int imageType(BufferedImage image) {
        return image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    }

    private static boolean hasTransparentPixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0xFF) return true;
            }
        }
        return false;
    }

    private static BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) return image;
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(java.awt.Color.WHITE); // any (fully opaque) alpha channel flattens onto white
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private byte[] encodeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT); // progressive: shows early on slow links
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
