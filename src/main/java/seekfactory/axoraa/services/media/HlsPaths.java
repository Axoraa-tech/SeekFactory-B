package seekfactory.axoraa.services.media;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Names for the adaptive-streaming (HLS) copy of a seek video. A video stored as
 * {@code media/<uuid>.<ext>} gets its streaming files in one folder {@code media/hls/<uuid>/}:
 * {@code master.m3u8} (lists the qualities), one playlist per quality ({@code stream_0.m3u8} = 360p,
 * {@code stream_1.m3u8} = 720p) and their numbered segments ({@code stream_0_000.ts}). Paths are fixed-shape,
 * so a request can never reach other files.
 */
public final class HlsPaths {

    public static final String PREFIX = "hls/";
    public static final String MASTER = "master.m3u8";
    private static final String UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    /** A file inside one video's HLS folder, relative to that folder. */
    private static final Pattern FILE = Pattern.compile("^(?:master\\.m3u8|stream_[0-3]\\.m3u8|stream_[0-3]_\\d{3,5}\\.ts)$");
    private static final Pattern ID = Pattern.compile("^" + UUID + "$");
    private static final Pattern VIDEO_KEY = Pattern.compile("/api/v1/media/(" + UUID + ")\\.(?:mp4|mov|webm)$");

    private HlsPaths() {
    }

    public static boolean isValid(String id, String file) {
        return id != null && file != null && ID.matcher(id).matches() && FILE.matcher(file).matches();
    }

    /** Storage path (below {@code media/}) of one HLS file, e.g. {@code hls/<uuid>/stream_0_001.ts}. */
    public static String path(String id, String file) {
        if (!isValid(id, file)) throw new IllegalArgumentException("Not an HLS file: " + id + "/" + file);
        return PREFIX + id + "/" + file;
    }

    /** The address the apps use: {@code /api/v1/media/hls/<uuid>/master.m3u8}. */
    public static String masterUrl(String id) {
        return MediaTypes.PUBLIC_PATH + path(id, MASTER);
    }

    /** The id of a stored seek video ({@code /api/v1/media/<uuid>.mp4}), if it is one of ours. */
    public static Optional<String> videoId(String videoUrl) {
        if (videoUrl == null) return Optional.empty();
        Matcher m = VIDEO_KEY.matcher(videoUrl);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /** The video's key below {@code /api/v1/media/}, e.g. {@code <uuid>.mp4}. */
    public static Optional<String> videoKey(String videoUrl) {
        if (videoUrl == null) return Optional.empty();
        int at = videoUrl.lastIndexOf('/');
        return videoId(videoUrl).map(id -> videoUrl.substring(at + 1));
    }

    /**
     * The streaming address to hand out for a seek: only when it was made from the seek's current video,
     * so replacing the video falls back to the plain file until the new streaming copy is ready.
     */
    public static String currentFor(String videoUrl, String hlsUrl) {
        if (hlsUrl == null || hlsUrl.isBlank()) return null;
        return videoId(videoUrl).map(HlsPaths::masterUrl).filter(hlsUrl::equals).orElse(null);
    }

    public static String contentTypeOf(String file) {
        return file.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/mp2t";
    }

    /** Segments never change; playlists may be rebuilt (e.g. a better encode), so they are re-checked hourly. */
    public static boolean isImmutable(String file) {
        return file.endsWith(".ts");
    }
}
