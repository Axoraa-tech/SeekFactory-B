package seekfactory.axoraa.dto.Request.comment;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyCreateRequest {

    @NotBlank(message = "Reply content is required")
    private String content;
}