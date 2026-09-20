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
public class StartConversationRequest {

    @NotBlank(message = "Manufacturer ID is required")
    private String manufacturerId;

    private String initialMessage;
    private String productId;
}
