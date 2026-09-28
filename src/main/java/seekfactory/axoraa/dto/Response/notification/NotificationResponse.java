package seekfactory.axoraa.dto.Response.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private String id;
    private String title;
    private String body;
    private String createdAt;
    private boolean read;
    /** Lower-case NotificationType: system, quote, rfq, message, follow, order. */
    private String type;
    /** Id of the RFQ, conversation, order or factory the notification is about. */
    private String referenceId;
}