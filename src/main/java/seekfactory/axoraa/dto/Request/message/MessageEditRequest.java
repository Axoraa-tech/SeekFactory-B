package seekfactory.axoraa.dto.Request.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** New text for a message the sender already sent. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageEditRequest {

    @NotBlank(message = "Message can't be empty")
    @Size(max = 5000, message = "Message is too long")
    private String messageText;
}
