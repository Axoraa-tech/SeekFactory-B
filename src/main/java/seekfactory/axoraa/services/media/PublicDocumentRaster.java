package seekfactory.axoraa.services.media;

import org.springframework.web.multipart.MultipartFile;
import seekfactory.axoraa.exceptions.BadRequestException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/** Public certificate scans are documents: validate their signature without decoding or re-encoding. */
final class PublicDocumentRaster {
    private static final Map<String, String> TYPES = Map.of(
            "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "webp", "image/webp", "gif", "image/gif", "avif", "image/avif");
    private static final int HEADER_LIMIT = 4096;

    private PublicDocumentRaster() {}

    /** Returns null for an existing non-raster document format. Does not affect private attachments. */
    static MediaTypes.Accepted validateIfSupported(MultipartFile file, MediaTypes.Limits limits) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return null;
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String expectedType = TYPES.get(ext);
        if (expectedType == null) return null;
        // Preserve both existing limits: a scan cannot evade the image cap by choosing document.
        long maxBytes = Math.min(limits.image(), limits.document());
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("File exceeds " + (maxBytes / (1024 * 1024)) + "MB limit");
        }
        if (file.getContentType() == null || !expectedType.equals(file.getContentType().toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("Document image extension and content type must match");
        }
        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(HEADER_LIMIT);
        } catch (IOException e) {
            throw new BadRequestException("Cannot read document image");
        }
        boolean matches = switch (ext) {
            case "png" -> starts(header, new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            case "jpg", "jpeg" -> starts(header, new byte[]{(byte) 255, (byte) 216, (byte) 255});
            case "gif" -> ascii(header, 0, "GIF87a") || ascii(header, 0, "GIF89a");
            case "webp" -> isWebp(header, file.getSize());
            case "avif" -> isAvif(header, file.getSize());
            default -> false;
        };
        if (!matches) throw new BadRequestException("Document image signature does not match its type");
        return new MediaTypes.Accepted("document", "jpeg".equals(ext) ? "jpg" : ext);
    }

    private static boolean starts(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false;
        return true;
    }

    private static boolean ascii(byte[] bytes, int offset, String value) {
        if (bytes.length < offset + value.length()) return false;
        byte[] expected = value.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < expected.length; i++) if (bytes[offset + i] != expected[i]) return false;
        return true;
    }

    // RFC 9649: RIFF length excludes its eight-byte header; the first chunk identifies WebP encoding.
    private static boolean isWebp(byte[] bytes, long size) {
        if (bytes.length < 20 || !ascii(bytes, 0, "RIFF") || !ascii(bytes, 8, "WEBP")) return false;
        long riffSize = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
        return riffSize + 8 == size && (ascii(bytes, 12, "VP8 ") || ascii(bytes, 12, "VP8L") || ascii(bytes, 12, "VP8X"));
    }

    // AVIF requires ftyp first. Inspect only the bounded brand table, never decode the image.
    private static boolean isAvif(byte[] bytes, long size) {
        if (bytes.length < 16 || !ascii(bytes, 4, "ftyp")) return false;
        long boxSize = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, 0, 4).getInt());
        if (boxSize < 16 || boxSize > bytes.length || boxSize >= size || boxSize % 4 != 0) return false;
        if (ascii(bytes, 8, "avif") || ascii(bytes, 8, "avis")) return true;
        for (int offset = 16; offset < boxSize; offset += 4) {
            if (ascii(bytes, offset, "avif") || ascii(bytes, offset, "avis")) return true;
        }
        return false;
    }
}
