package seekfactory.axoraa.dto.Response.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Order request as seen by the buyer (own orders) or the seller (incoming orders).
 * {@code buyer} contact details are only populated for the seller, who needs them
 * to follow up; the buyer is told at order time that they will be shared.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private String id;
    private String referenceNumber;
    private String status;
    private String statusUpdatedAt;
    private String createdAt;

    private String productId;
    private String productSlug;
    private String productName;
    private String productImageUrl;
    private BigDecimal unitPriceInr;
    private String unit;
    private Integer quantity;
    /** unitPriceInr × quantity at order time (listing price; final price is negotiated). */
    private BigDecimal estimatedTotalInr;

    private String buyerNote;
    private String sellerNote;

    /** DIRECT (product page), CART or RFQ_QUOTE. */
    private String source;
    /** RFQ the accepted quote belongs to, for RFQ_QUOTE orders. */
    private String rfqId;
    /** Currency of quotedTotal (listing prices are INR). */
    private String currency;
    /** Total the factory quoted, for RFQ_QUOTE orders. */
    private BigDecimal quotedTotal;
    private String contactName;
    private String contactPhone;
    private String deliveryAddress;
    private String cancelReason;
    /** Whether the buyer may still cancel (before the deal is confirmed). */
    private Boolean cancellable;

    private Party manufacturer;
    private Party buyer;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Party {
        private String id;
        private String name;
        private String slug;
        private String companyName;
        private String country;
        private String avatarUrl;
        private String email;
        private String phone;
    }
}
