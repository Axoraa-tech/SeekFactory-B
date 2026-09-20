package seekfactory.axoraa.dto.Request.reel;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReelCreateRequest {

    @NotBlank(message = "Reel title is required")
    private String title;

    private String description;

    @NotBlank(message = "Poster URL is required")
    private String posterUrl;

    private String videoUrl;
    private Integer durationSec;
    private List<String> hashtags;
    private List<String> productIds;
}