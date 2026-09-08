package seekfactory.axoraa.dto.Request.message;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSendRequest {

    @NotBlank(message = "Message text is required")
    private String messageText;

    private String attachmentName;
    private String attachmentSize;
    private String attachmentUrl;
}