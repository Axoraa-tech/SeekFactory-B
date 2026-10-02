package seekfactory.axoraa.dto.Response.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {

    private String id;
    private String manufacturerId;
    private String buyerId;
    private String buyerName;
    private String buyerCompany;
    private String buyerAvatarUrl;
    private String lastMessage;
    private String lastMessageAt;
    private int unreadCount;
    /** The other side of the chat is signed in and active right now (the green dot). */
    private boolean counterpartOnline;
    /** When the other side was last active, if known (ISO-8601). */
    private String counterpartLastSeenAt;
    private ManufacturerResponse manufacturer;
}