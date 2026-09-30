package seekfactory.axoraa.utils;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Generates URL-safe slugs from display names.
 * Example: "Apex Forgings Pvt. Ltd." → "apex-forgings-pvt-ltd"
 */
public final class SlugUtils {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SUFFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    /** Readable part of a generated slug; the column is VARCHAR(128) and the suffix adds 9. */
    static final int MAX_BASE_LENGTH = 100;

    /**
     * A slug that cannot realistically collide: the name's ASCII letters and digits, then an
     * 8-character random suffix (36^8 ≈ 2.8 trillion values). Names without Latin letters
     * (e.g. Chinese) fall back to {@code fallback}, so the URL is never just "-1234".
     * Example: "5-Axis CNC Center" → "5-axis-cnc-center-k3v9x2qa"
     */
    public static String uniqueSlug(String name, String fallback) {
        String base = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        base = base.replaceAll("^-+|-+$", "");
        if (base.length() > MAX_BASE_LENGTH) {
            base = base.substring(0, MAX_BASE_LENGTH).replaceAll("-+$", "");
        }
        if (base.isEmpty()) {
            base = fallback;
        }
        return base + "-" + randomSuffix(8);
    }

    static String randomSuffix(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(SUFFIX_ALPHABET.charAt(RANDOM.nextInt(SUFFIX_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    private SlugUtils() {}

    public static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String slug = WHITESPACE.matcher(normalized).replaceAll("-");
        slug = NON_LATIN.matcher(slug).replaceAll("");
        slug = slug.toLowerCase(Locale.ENGLISH);
        slug = slug.replaceAll("-{2,}", "-");       // collapse multiple hyphens
        slug = slug.replaceAll("^-|-$", "");        // trim leading/trailing hyphens
        return slug;
    }
}