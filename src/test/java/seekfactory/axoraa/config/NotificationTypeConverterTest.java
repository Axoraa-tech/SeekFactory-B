package seekfactory.axoraa.config;

import org.junit.jupiter.api.Test;
import seekfactory.axoraa.enums.NotificationType;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTypeConverterTest {

    private final NotificationTypeConverter converter = new NotificationTypeConverter();

    @Test
    void knownValuesRoundTrip() {
        for (NotificationType type : NotificationType.values()) {
            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(type))).isEqualTo(type);
        }
    }

    @Test
    void legacySeedValuesMapToClosestType() {
        assertThat(converter.convertToEntityAttribute("WELCOME")).isEqualTo(NotificationType.SYSTEM);
        assertThat(converter.convertToEntityAttribute("RFQ_MATCH")).isEqualTo(NotificationType.RFQ);
        assertThat(converter.convertToEntityAttribute("RFQ_QUOTE")).isEqualTo(NotificationType.QUOTE);
        assertThat(converter.convertToEntityAttribute("ORDER_STATUS")).isEqualTo(NotificationType.ORDER);
    }

    @Test
    void unknownOrEmptyFallsBackToSystem() {
        assertThat(converter.convertToEntityAttribute("SOMETHING_NEW")).isEqualTo(NotificationType.SYSTEM);
        assertThat(converter.convertToEntityAttribute(null)).isEqualTo(NotificationType.SYSTEM);
        assertThat(converter.convertToEntityAttribute(" quote ")).isEqualTo(NotificationType.QUOTE);
    }
}
