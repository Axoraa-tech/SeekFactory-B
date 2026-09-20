package seekfactory.axoraa.dto.Request.rfq;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RfqQuoteRequest {

    @NotNull(message = "Quotation price is required")
    @Positive(message = "Quotation price must be positive")
    private BigDecimal quotePrice;

    private String currency; // "INR", "USD"

    @NotNull(message = "Lead time in days is required")
    @Positive(message = "Lead time must be positive")
    private Integer leadTimeDays;

    private String notes;
    private String attachmentUrl;
}