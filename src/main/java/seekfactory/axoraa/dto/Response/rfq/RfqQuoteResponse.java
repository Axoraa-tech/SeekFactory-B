package seekfactory.axoraa.dto.Response.rfq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;

import java.math.BigDecimal;

/**
 * A factory's quote on a buyer's RFQ, as the buyer sees it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RfqQuoteResponse {
    private String id;
    private ManufacturerResponse manufacturer;
    private BigDecimal quotePrice;
    private String currency;
    private Integer leadTimeDays;
    private String notes;
    private String attachmentUrl;
    private String status;
    private String createdAt;
    /** Set once the quote is accepted and turned into an order. */
    private String orderId;
}
