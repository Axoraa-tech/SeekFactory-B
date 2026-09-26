package seekfactory.axoraa.dto.Response.settings;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * How seeks are showcased on the website home feed.
 *
 * @param mode        how seeks are arranged on the home feed; see {@link Mode}
 * @param updatedAt   read-only; ignored on update
 * @param updatedBy   read-only display name of the last admin who changed it; ignored on update
 */
public record FeedShowcaseSettings(
        @NotNull Mode mode,
        boolean autoplay,
        boolean showProfile,
        boolean showPhotos,
        Instant updatedAt,
        String updatedBy) {

    /**
     * Unknown values fall back to DUAL, so an older backend reading a newer setting
     * still serves a working feed rather than failing.
     */
    public enum Mode {
        /** Two independent video columns. */
        DUAL,
        /** One video with profile and photo panels. */
        SINGLE,
        /** One seek per row down the centre column. */
        FEED,
        /** Dense rows: thumbnail left, details right. */
        COMPACT,
        /** Responsive card grid of posters. */
        GRID,
        /** One hero seek above a horizontal rail of the rest. */
        SPOTLIGHT;

        public static Mode parseOrDefault(String value) {
            try {
                return value == null ? DUAL : Mode.valueOf(value);
            } catch (IllegalArgumentException e) {
                return DUAL;
            }
        }
    }

    public static FeedShowcaseSettings defaults() {
        return new FeedShowcaseSettings(Mode.DUAL, true, true, true, null, null);
    }
}
