package seekfactory.axoraa.dto.Request.analytics;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optional body for view tracking. {@code viewerId} is a random id the client keeps
 * (e.g. in localStorage) so guest views can be deduplicated; ignored for signed-in users.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ViewEventRequest {

    @Size(max = 64)
    private String viewerId;
}
