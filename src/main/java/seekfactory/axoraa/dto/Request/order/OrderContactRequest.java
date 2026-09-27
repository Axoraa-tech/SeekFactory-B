package seekfactory.axoraa.dto.Request.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Delivery contact for a cart checkout or an accepted RFQ quote; shared with the factory. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderContactRequest {

    @NotBlank(message = "Contact name is required")
    @Size(max = 255, message = "Contact name is too long")
    private String contactName;

    @NotBlank(message = "Contact phone is required")
    @Size(max = 50, message = "Contact phone is too long")
    private String contactPhone;

    @NotBlank(message = "Delivery address is required")
    @Size(max = 2000, message = "Delivery address is too long")
    private String deliveryAddress;

    @Size(max = 2000, message = "Note must be at most 2000 characters")
    private String note;
}
