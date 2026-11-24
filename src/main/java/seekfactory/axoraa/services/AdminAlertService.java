package seekfactory.axoraa.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import seekfactory.axoraa.services.services.MailService;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Emails the admins when something needs their attention: a new buyer, a new manufacturer waiting for
 * approval, new content (product or seek) or a new plan payment to review.
 *
 * Configured in Admin → Settings → Email alerts (stored as the {@code admin_alerts} platform setting):
 * a master switch, the recipients and one switch per event. Sending happens after the triggering
 * transaction commits and never affects it; mail problems are only logged (see MailService).
 */
@Slf4j
@Service
public class AdminAlertService {

    public enum Event { NEW_USER, NEW_MANUFACTURER, NEW_CONTENT, NEW_PAYMENT }

    /** What the admin chose. {@code recipients} are email addresses. */
    public record Settings(boolean enabled, List<String> recipients, boolean newUser, boolean newManufacturer,
                           boolean newContent, boolean newPayment, Instant updatedAt) {
        public static Settings defaults() {
            return new Settings(false, List.of(), true, true, true, true, null);
        }

        boolean wants(Event event) {
            if (!enabled || recipients.isEmpty()) return false;
            return switch (event) {
                case NEW_USER -> newUser;
                case NEW_MANUFACTURER -> newManufacturer;
                case NEW_CONTENT -> newContent;
                case NEW_PAYMENT -> newPayment;
            };
        }
    }

    static final String KEY = "admin_alerts";
    static final int MAX_RECIPIENTS = 10;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final JdbcTemplate jdbcTemplate;
    private final MailService mailService;
    private final String siteUrl;
    static final String DEFAULT_SITE_URL = "https://www.seekfactory.com";

    public AdminAlertService(JdbcTemplate jdbcTemplate, MailService mailService,
                             @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.mailService = mailService;
        this.siteUrl = publicSiteUrl(frontendUrl);
    }

    public Settings getSettings() {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT coalesce((value->>'enabled')::boolean, false)        AS enabled,
                           coalesce(value->>'recipients', '')                   AS recipients,
                           coalesce((value->>'newUser')::boolean, true)         AS new_user,
                           coalesce((value->>'newManufacturer')::boolean, true) AS new_manufacturer,
                           coalesce((value->>'newContent')::boolean, true)      AS new_content,
                           coalesce((value->>'newPayment')::boolean, true)      AS new_payment,
                           updated_at
                    FROM platform_settings WHERE key = ?
                    """, (rs, i) -> new Settings(
                    rs.getBoolean("enabled"), parseRecipients(rs.getString("recipients")),
                    rs.getBoolean("new_user"), rs.getBoolean("new_manufacturer"),
                    rs.getBoolean("new_content"), rs.getBoolean("new_payment"),
                    rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toInstant()), KEY);
        } catch (EmptyResultDataAccessException e) {
            return Settings.defaults();
        }
    }

    public Settings updateSettings(Settings s, String adminId) {
        List<String> recipients = cleanRecipients(s.recipients());
        if (s.enabled() && recipients.isEmpty()) {
            throw new seekfactory.axoraa.exceptions.BadRequestException("Add at least one email address to turn alerts on");
        }
        jdbcTemplate.update("""
                INSERT INTO platform_settings (key, value, updated_by, updated_at)
                VALUES (?, jsonb_build_object('enabled', ?, 'recipients', ?::text, 'newUser', ?, 'newManufacturer', ?,
                                              'newContent', ?, 'newPayment', ?), ?, now())
                ON CONFLICT (key) DO UPDATE
                  SET value = EXCLUDED.value, updated_by = EXCLUDED.updated_by, updated_at = now()
                """, KEY, s.enabled(), String.join(",", recipients), s.newUser(), s.newManufacturer(),
                s.newContent(), s.newPayment(), adminId);
        log.info("Admin {} updated email alerts (on: {}, {} recipients)", adminId, s.enabled(), recipients.size());
        return getSettings();
    }

    /** Sends a test message to the saved recipients, whatever the switches say. Returns how many. */
    public int sendTest() {
        List<String> to = getSettings().recipients();
        if (to.isEmpty()) throw new seekfactory.axoraa.exceptions.BadRequestException("Add and save at least one email address first");
        String body = AlertEmail.render(
                "Email alerts are working",
                "This is a test message. From now on, this address gets an email whenever something on SeekFactory needs an admin.",
                List.of(
                        new String[]{"New manufacturer", "registered and waiting for approval"},
                        new String[]{"New buyer", "someone signed up"},
                        new String[]{"New content", "a product or seek was added"},
                        new String[]{"New payment", "a plan payment is waiting for review"}),
                "Nothing to do. You can change which alerts you get in Admin > Settings > Email alerts.",
                "Open the admin panel", siteUrl + "/admin/dashboard",
                Instant.now());
        for (String recipient : to) mailService.send(recipient, "[SeekFactory] Email alerts are working", body);
        return to.size();
    }

    // ---- events (called by the services that create things) ----

    public void newUser(String name, String email, String phone, String country) {
        notify(Event.NEW_USER, "New buyer signed up: " + display(name, email, phone), AlertEmail.render(
                "New buyer signed up",
                "A new buyer account was created on SeekFactory.",
                rows("Name", name, "Email", email, "Phone", phone, "Country", country),
                "No approval needed. Review the account if anything looks wrong.",
                "View users", siteUrl + "/admin/users", Instant.now()));
    }

    public void newManufacturer(String company, String email, String phone, String country) {
        notify(Event.NEW_MANUFACTURER, "Manufacturer waiting for approval: " + display(company, email, phone), AlertEmail.render(
                "New manufacturer waiting for approval",
                "A manufacturer registered on SeekFactory. Their factory stays hidden from buyers until an admin approves it.",
                rows("Company", company, "Email", email, "Phone", phone, "Country", country),
                "Check the company details and approve or reject the factory.",
                "Review manufacturers", siteUrl + "/admin/manufacturers", Instant.now()));
    }

    public void newContent(String kind, String title, String manufacturer) {
        String what = "seek".equals(kind) ? "seek video" : kind;
        notify(Event.NEW_CONTENT, "New " + what + " from " + safe(manufacturer) + ": " + safe(title), AlertEmail.render(
                "New " + what + " added",
                safe(manufacturer) + " published a new " + what + " on SeekFactory.",
                rows("Manufacturer", manufacturer, "Title", title, "Type", what),
                "Check that it follows the content rules. Take it down from Content if it does not.",
                "Review content", siteUrl + "/admin/content", Instant.now()));
    }

    public void newPayment(String payer, String plan, String amount) {
        notify(Event.NEW_PAYMENT, "Payment to review: " + safe(plan) + " from " + safe(payer), AlertEmail.render(
                "New payment waiting for review",
                "A buyer submitted a plan payment with proof of payment.",
                rows("From", payer, "Plan", plan, "Amount", amount),
                "Check the payment proof, then approve or reject it. The plan starts only after approval.",
                "Review payments", siteUrl + "/admin/payments", Instant.now()));
    }

    private void notify(Event event, String subject, String body) {
        Runnable send = () -> {
            try {
                Settings settings = getSettings();
                if (!settings.wants(event)) return;
                for (String to : settings.recipients()) mailService.send(to, "[SeekFactory] " + subject, body);
            } catch (RuntimeException e) {
                log.warn("Admin alert '{}' not sent: {}", subject, e.getMessage());
            }
        };
        // Only for things that were really saved: wait for the commit when there is a transaction
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    /** Label/value pairs; empty values are left out. */
    private static List<String[]> rows(String... labelsAndValues) {
        List<String[]> out = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < labelsAndValues.length; i += 2) {
            String v = labelsAndValues[i + 1];
            if (v != null && !v.isBlank()) out.add(new String[]{labelsAndValues[i], v});
        }
        return out;
    }

    /**
     * The public website address for links in emails. A local address (app.frontend-url left at its
     * development default) would be useless to the admin reading the email, so the live site is used.
     */
    static String publicSiteUrl(String configured) {
        String url = configured == null ? "" : configured.trim().replaceAll("/+$", "");
        if (url.isEmpty() || url.matches("(?i)https?://(localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0)(:\\d+)?(/.*)?")) {
            return DEFAULT_SITE_URL;
        }
        return url;
    }

    // ---- helpers ----

    static List<String> parseRecipients(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return cleanRecipients(Arrays.asList(raw.split("[,;\\s]+")));
    }

    static List<String> cleanRecipients(List<String> raw) {
        if (raw == null) return List.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String r : raw) {
            if (r == null) continue;
            String e = r.trim().toLowerCase(Locale.ROOT);
            if (e.isEmpty()) continue;
            if (!EMAIL.matcher(e).matches()) {
                throw new seekfactory.axoraa.exceptions.BadRequestException("Not a valid email address: " + e);
            }
            out.add(e);
        }
        if (out.size() > MAX_RECIPIENTS) {
            throw new seekfactory.axoraa.exceptions.BadRequestException("At most " + MAX_RECIPIENTS + " email addresses");
        }
        return List.copyOf(out);
    }

    private static String display(String name, String email, String phone) {
        String n = safe(name);
        String contact = email != null && !email.isBlank() ? email : phone;
        return contact == null || contact.isBlank() ? n : n + " (" + contact + ")";
    }

    /** One line, bounded: user-entered names go into email subjects. */
    private static String safe(String value) {
        if (value == null || value.isBlank()) return "(no name)";
        String one = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return one.length() > 80 ? one.substring(0, 80) + "…" : one;
    }
}
