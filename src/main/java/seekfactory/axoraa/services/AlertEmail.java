package seekfactory.axoraa.services;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The plain-text layout of admin alert emails. Plain text on purpose: no HTML, images or tracking,
 * which keeps them out of spam folders and readable in any mail app.
 *
 * <pre>
 * SEEKFACTORY ADMIN ALERT
 * ==================================================
 *
 * New manufacturer waiting for approval
 *
 * A manufacturer registered on SeekFactory. ...
 *
 * DETAILS
 * --------------------------------------------------
 *   Company   : Acme Machines
 *   Email     : sales@acme.cn
 *
 * WHAT TO DO
 * --------------------------------------------------
 * Check the company details and approve or reject the factory.
 *
 * Review manufacturers:
 * https://www.seekfactory.com/admin/manufacturers
 *
 * --------------------------------------------------
 * Sent 22 Nov 2026, 8:15 PM IST
 * SeekFactory - verified Chinese machinery manufacturers for Indian buyers
 * ...
 * </pre>
 */
final class AlertEmail {

    private static final String RULE = "-".repeat(50);
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a 'IST'", Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata"));

    private AlertEmail() {
    }

    static String render(String title, String intro, List<String[]> details, String action,
                         String linkLabel, String link, Instant sentAt) {
        int width = details.stream().mapToInt(d -> d[0].length()).max().orElse(0);
        StringBuilder b = new StringBuilder();
        b.append("SEEKFACTORY ADMIN ALERT\n").append("=".repeat(50)).append("\n\n");
        b.append(clean(title)).append("\n\n");
        b.append(clean(intro)).append("\n\n");
        if (!details.isEmpty()) {
            b.append("DETAILS\n").append(RULE).append('\n');
            for (String[] d : details) {
                b.append("  ").append(String.format("%-" + width + "s", d[0])).append(" : ").append(clean(d[1])).append('\n');
            }
            b.append('\n');
        }
        b.append("WHAT TO DO\n").append(RULE).append('\n');
        b.append(clean(action)).append("\n\n");
        b.append(linkLabel).append(":\n").append(link).append("\n\n");
        b.append(RULE).append('\n');
        b.append("Sent ").append(WHEN.format(sentAt)).append('\n');
        b.append("SeekFactory - verified Chinese machinery manufacturers for Indian buyers\n");
        b.append("You receive this because your address is listed in Admin > Settings > Email alerts.\n");
        b.append("To stop or change these emails, update that page.\n");
        return b.toString();
    }

    /** One line, bounded, so text a user typed cannot reshape the email. */
    private static String clean(String value) {
        if (value == null || value.isBlank()) return "-";
        String one = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return one.length() > 300 ? one.substring(0, 300) + "..." : one;
    }
}
