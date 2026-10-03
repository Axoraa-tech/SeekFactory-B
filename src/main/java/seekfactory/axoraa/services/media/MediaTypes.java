package seekfactory.axoraa.services.media;

import org.springframework.web.multipart.MultipartFile;
import seekfactory.axoraa.exceptions.BadRequestException;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * What uploads are accepted and how stored files are named, shared by every
 * {@link seekfactory.axoraa.services.services.MediaStorageService} implementation.
 *
 * Only allowlisted raster images, video containers and document/CAD formats are accepted:
 * SVG/HTML could carry script. Files are renamed to random UUID keys, so client-supplied names
 * never reach the storage layer.
 */
public final class MediaTypes {

    /** Stored references look like {@code /api/v1/media/<uuid>.<ext>}, independent of where files live. */
    public static final String PUBLIC_PATH = "/api/v1/media/";

    public static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp",
            "image/gif", "gif");

    public static final Map<String, String> VIDEO_TYPES = Map.of(
            "video/mp4", "mp4",
            "video/webm", "webm",
            "video/quicktime", "mov");

    /**
     * Drawings and specs buyers attach to RFQs and chats. Browsers report CAD files with
     * inconsistent (often generic) content types, so documents are allowlisted by extension.
     */
    public static final Map<String, String> DOCUMENT_EXTENSIONS = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("dwg", "application/octet-stream"),
            Map.entry("dxf", "application/octet-stream"),
            Map.entry("step", "application/octet-stream"),
            Map.entry("stp", "application/octet-stream"),
            Map.entry("igs", "application/octet-stream"),
            Map.entry("iges", "application/octet-stream"),
            Map.entry("stl", "application/octet-stream"),
            Map.entry("zip", "application/zip"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));

    private static final Map<String, String> EXTENSION_TYPES;

    static {
        Map<String, String> types = new HashMap<>(Map.of(
                "png", "image/png",
                "jpg", "image/jpeg",
                "webp", "image/webp",
                "gif", "image/gif",
                "mp4", "video/mp4",
                "webm", "video/webm",
                "mov", "video/quicktime"));
        types.putAll(DOCUMENT_EXTENSIONS);
        EXTENSION_TYPES = Map.copyOf(types);
    }

    public static final Pattern KEY_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\."
                    + "(png|jpg|webp|gif|mp4|webm|mov|pdf|dwg|dxf|step|stp|igs|iges|stl|zip|xlsx|docx)$");

    public static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|webp|gif|pdf|dwg|dxf|step|stp|igs|iges|stl|zip|xlsx|docx)$");

    public static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    /** Per-kind upload limits in bytes. */
    public record Limits(long image, long video, long document) {}

    /** A validated upload: what it is and the extension it will be stored with. */
    public record Accepted(String kind, String extension) {
        public boolean isImage() {
            return "image".equals(kind);
        }

        public boolean isVideo() {
            return "video".equals(kind);
        }
    }

    private MediaTypes() {}

    /** Content type to serve a stored key or file name with, from its allowlisted extension. */
    public static String contentTypeOf(String keyOrName) {
        String extension = keyOrName.substring(keyOrName.lastIndexOf('.') + 1).toLowerCase();
        return EXTENSION_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    /**
     * Public uploads (product photos, seek videos, datasheets).
     *
     * @param kind "image", "video" or "document"
     */
    public static Accepted validatePublic(MultipartFile file, String kind, Limits limits) {
        requireFile(file);
        if ("document".equals(kind)) {
            return new Accepted("document", documentExtension(file, limits.document(),
                    "Unsupported document type. Allowed: " + String.join(", ", new TreeSet<>(DOCUMENT_EXTENSIONS.keySet()))));
        }
        Map<String, String> allowed;
        long maxBytes;
        if ("image".equals(kind)) {
            allowed = IMAGE_TYPES;
            maxBytes = limits.image();
        } else if ("video".equals(kind)) {
            allowed = VIDEO_TYPES;
            maxBytes = limits.video();
        } else {
            throw new BadRequestException("kind must be 'image', 'video' or 'document'");
        }
        String contentType = file.getContentType();
        String extension = contentType == null ? null : allowed.get(contentType.toLowerCase());
        if (extension == null) {
            throw new BadRequestException("Unsupported " + kind + " type: " + (contentType == null ? "unknown" : contentType));
        }
        requireSize(file, maxBytes);
        return new Accepted(kind, extension);
    }

    /** Chat attachments: raster images by content type; PDFs and drawings by extension. */
    public static Accepted validatePrivate(MultipartFile file, Limits limits) {
        requireFile(file);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String imageExtension = IMAGE_TYPES.get(contentType);
        if (imageExtension != null) {
            requireSize(file, limits.image());
            return new Accepted("image", imageExtension);
        }
        return new Accepted("document", documentExtension(file, limits.document(),
                "Attach an image, PDF, CAD drawing (STEP, DWG, DXF, IGES, STL), ZIP, XLSX or DOCX"));
    }

    public static void requireNamespace(String namespace) {
        if (namespace == null || !NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new BadRequestException("Invalid attachment namespace");
        }
    }

    private static String documentExtension(MultipartFile file, long maxBytes, String unsupportedMessage) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!DOCUMENT_EXTENSIONS.containsKey(extension)) {
            throw new BadRequestException(unsupportedMessage);
        }
        requireSize(file, maxBytes);
        return extension;
    }

    private static void requireFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }
    }

    private static void requireSize(MultipartFile file, long maxBytes) {
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("File exceeds " + (maxBytes / (1024 * 1024)) + "MB limit");
        }
    }
}
