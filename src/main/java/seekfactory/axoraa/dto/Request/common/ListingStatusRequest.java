package seekfactory.axoraa.dto.Request.common;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingStatusRequest {

    /** true = visible to buyers, false = paused (hidden, kept in the seller hub). */
    @NotNull(message = "listed is required")
    private Boolean listed;
}
