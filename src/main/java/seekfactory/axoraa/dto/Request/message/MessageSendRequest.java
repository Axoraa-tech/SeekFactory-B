package seekfactory.axoraa.dto.Request.message;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A chat message. Text is optional when an attachment is sent; one of the two is required
 * (enforced in ConversationService).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSendRequest {

    @Size(max = 5000, message = "Message must be at most 5000 characters")
    private String messageText;

    @Size(max = 255)
    private String attachmentName;
    @Size(max = 50)
    private String attachmentSize;
    /** Must be a URL returned by POST /api/v1/conversations/{id}/attachments for this conversation. */
    private String attachmentUrl;

    /** Optional order this message is about (must be between this buyer and factory). */
    @Size(max = 64)
    private String orderId;
}
