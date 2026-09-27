package seekfactory.axoraa.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import seekfactory.axoraa.enums.NotificationType;

import java.util.Locale;
import java.util.Map;

/**
 * Tolerant mapping for notifications.notification_type.
 *
 * The seed data (V5) and older builds wrote values the enum never had (WELCOME, RFQ_MATCH,
 * RFQ_QUOTE, ORDER_STATUS). With plain @Enumerated(STRING) a single such row made
 * GET /api/v1/notifications fail with a 500 for that user. Legacy values are mapped to
 * their closest type and anything unknown falls back to SYSTEM instead of failing.
 */
@Slf4j
@Converter
public class NotificationTypeConverter implements AttributeConverter<NotificationType, String> {

    private static final Map<String, NotificationType> LEGACY = Map.of(
            "WELCOME", NotificationType.SYSTEM,
            "RFQ_MATCH", NotificationType.RFQ,
            "RFQ_QUOTE", NotificationType.QUOTE,
            "ORDER_STATUS", NotificationType.ORDER);

    @Override
    public String convertToDatabaseColumn(NotificationType type) {
        return (type != null ? type : NotificationType.SYSTEM).name();
    }

    @Override
    public NotificationType convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) return NotificationType.SYSTEM;
        String key = value.trim().toUpperCase(Locale.ROOT);
        try {
            return NotificationType.valueOf(key);
        } catch (IllegalArgumentException e) {
            NotificationType mapped = LEGACY.get(key);
            if (mapped == null) {
                log.warn("Unknown notification_type '{}' read from DB; treating as SYSTEM", value);
                return NotificationType.SYSTEM;
            }
            return mapped;
        }
    }
}
