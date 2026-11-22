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
    private final String frontendUrl;

    public AdminAlertService(JdbcTemplate jdbcTemplate, MailService mailService,
                             @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.mailService = mailService;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
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
        for (String recipient : to) {
            mailService.send(recipient, "SeekFactory admin alerts are working",
                    "This is a test from SeekFactory Admin → Settings → Email alerts.\n\n"
                            + "You will get an email here when there is something to review.\n\n" + frontendUrl + "/admin/dashboard");
        }
        return to.size();
    }

    // ---- events (called by the services that create things) ----

    public void newUser(String name, String email, String phone, String country) {
        notify(Event.NEW_USER, "New buyer signed up: " + display(name, email, phone),
                "A new buyer joined SeekFactory.\n\n"
                        + line("Name", name) + line("Email", email) + line("Phone", phone) + line("Country", country)
                        + "\nView users: " + frontendUrl + "/admin/users");
    }

    public void newManufacturer(String company, String email, String phone, String country) {
        notify(Event.NEW_MANUFACTURER, "Manufacturer waiting for approval: " + display(company, email, phone),
                "A new manufacturer registered and is waiting for your approval.\n\n"
                        + line("Company", company) + line("Email", email) + line("Phone", phone) + line("Country", country)
                        + "\nReview and approve: " + frontendUrl + "/admin/manufacturers");
    }

    public void newContent(String kind, String title, String manufacturer) {
        notify(Event.NEW_CONTENT, "New " + kind + " from " + safe(manufacturer) + ": " + safe(title),
                safe(manufacturer) + " added a new " + kind + ".\n\n" + line("Title", title)
                        + "\nReview content: " + frontendUrl + "/admin/content");
    }

    public void newPayment(String payer, String plan, String amount) {
        notify(Event.NEW_PAYMENT, "Payment to review: " + safe(plan) + " from " + safe(payer),
                "A plan payment was submitted and is waiting for review.\n\n"
                        + line("From", payer) + line("Plan", plan) + line("Amount", amount)
                        + "\nReview payments: " + frontendUrl + "/admin/payments");
    }

    private void notify(Event event, String subject, String body) {
        Runnable send = () -> {
            try {
                Settings settings = getSettings();
                if (!settings.wants(event)) return;
                String text = body + "\n\n—\nYou get these because of Admin → Settings → Email alerts. Turn them off there.";
                for (String to : settings.recipients()) mailService.send(to, "[SeekFactory] " + subject, text);
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

    private static String line(String label, String value) {
        return value == null || value.isBlank() ? "" : label + ": " + value.replaceAll("[\\r\\n]+", " ") + "\n";
    }

    /** One line, bounded: user-entered names go into email subjects. */
    private static String safe(String value) {
        if (value == null || value.isBlank()) return "(no name)";
        String one = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return one.length() > 80 ? one.substring(0, 80) + "…" : one;
    }
}
