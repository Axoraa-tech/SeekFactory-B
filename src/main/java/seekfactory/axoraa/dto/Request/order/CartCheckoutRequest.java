package seekfactory.axoraa.dto.Request.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Cart checkout: the delivery contact plus, optionally, which lines to send and a note for each.
 * Without {@code items} every line is sent with the shared {@code note} (the original behaviour).
 * Lines that are not selected stay in the cart.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CartCheckoutRequest extends OrderContactRequest {

    @Valid
    @Size(min = 1, max = 100, message = "Select between 1 and 100 items")
    private List<Line> items;

    public CartCheckoutRequest(String contactName, String contactPhone, String deliveryAddress, String note,
                               List<Line> items) {
        super(contactName, contactPhone, deliveryAddress, note);
        this.items = items;
    }

    /** One selected cart line and the note sent to its factory. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {

        @NotBlank(message = "Cart item is required")
        private String cartItemId;

        @Size(max = 2000, message = "Note must be at most 2000 characters")
        private String note;
    }
}
