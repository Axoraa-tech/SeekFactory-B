package seekfactory.axoraa.dto.Response.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    private String id;
    private String conversationId;
    private String senderId;
    private String senderType; // "USER" or "FACTORY"
    private String senderName;
    private String senderAvatarUrl;
    private String messageText;
    private String attachmentName;
    private String attachmentSize;
    private String attachmentUrl;
    private String attachmentContentType;
    private boolean isRead;
    private String createdAt;

    /** The order this message is about, if the sender picked one. */
    private OrderContext order;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderContext {
        private String id;
        private String referenceNumber;
        private String productName;
        private String productSlug;
        private Integer quantity;
        private String unit;
        private String status;
    }
}
