package seekfactory.axoraa.dto.Request.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Seller moves an order request along (PENDING, CONTACTED, NEGOTIATING, CONFIRMED, COMPLETED, CANCELLED). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusUpdateRequest {

    @NotBlank(message = "Status is required")
    private String status;

    /** Optional note shown to the buyer, e.g. "Sent revised pricing by email". */
    @Size(max = 2000, message = "Note must be at most 2000 characters")
    private String note;
}
