package seekfactory.axoraa.dto.Request.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Buyer places an order request for a product (identified by its public slug). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateRequest {

    @NotBlank(message = "Product is required")
    @Size(max = 255)
    private String productSlug;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 1_000_000, message = "Quantity is too large")
    private Integer quantity;

    @Size(max = 2000, message = "Note must be at most 2000 characters")
    private String note;

    // Optional delivery contact, shared with the factory with the request
    @Size(max = 255, message = "Contact name is too long")
    private String contactName;

    @Size(max = 50, message = "Contact phone is too long")
    private String contactPhone;

    @Size(max = 2000, message = "Delivery address is too long")
    private String deliveryAddress;

    public OrderCreateRequest(String productSlug, Integer quantity, String note) {
        this(productSlug, quantity, note, null, null, null);
    }
}
