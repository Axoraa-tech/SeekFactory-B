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
    private ManufacturerResponse manufacturer;
}