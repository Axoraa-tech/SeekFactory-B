package seekfactory.axoraa.dto.Request.reel;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Partial update of a seller's seek: null fields are left unchanged. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReelUpdateRequest {

    @Size(min = 1, max = 255, message = "Title must be 1-255 characters")
    private String title;

    private String description;
    private String posterUrl;
    private String videoUrl;
    private Integer durationSec;
    private List<String> hashtags;
    private List<String> productIds;
}
