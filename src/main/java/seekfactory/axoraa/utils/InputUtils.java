package seekfactory.axoraa.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small parsing and validation helpers for buyer input.
 */
public final class InputUtils {

    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*([0-9][0-9,]*)");

    private InputUtils() {}

    /** Leading integer in free text such as "500 pcs" or "1,000 units"; the fallback when there is none. */
    public static int leadingInt(String text, int fallback) {
        if (text == null) return fallback;
        Matcher m = LEADING_NUMBER.matcher(text);
        if (!m.find()) return fallback;
        try {
            int value = Integer.parseInt(m.group(1).replace(",", ""));
            return value > 0 ? value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Media references must be http(s) URLs or server-relative paths (e.g. /api/v1/media/...),
     * never javascript:/data: URIs that would be rendered into pages.
     */
    public static boolean isSafeUrl(String url) {
        if (url == null || url.isBlank()) return true;
        String u = url.trim().toLowerCase();
        return u.startsWith("https://") || u.startsWith("http://") || (u.startsWith("/") && !u.startsWith("//"));
    }

    /** LIKE pattern for a case-insensitive substring search; "%" (match all) for blank input. */
    public static String likePattern(String query) {
        if (query == null || query.isBlank()) return "%";
        String cleaned = query.trim().toLowerCase().replace("\\", "").replace("%", "").replace("_", "");
        return cleaned.isEmpty() ? "%" : "%" + cleaned + "%";
    }
}
