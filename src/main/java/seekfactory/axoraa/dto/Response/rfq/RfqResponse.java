package seekfactory.axoraa.dto.Response.rfq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RfqResponse {

    private String id;
    private String referenceNumber;
    private String productName;
    private String categoryId;
    private String quantity;
    private String unit;
    private String targetPrice;
    private String currency;
    private String incoterm;
    private String companyName;
    private String details;
    private String attachmentName;
    private String attachmentSize;
    private String attachmentUrl;
    private String status;
    private String createdAt;

    // ── Seller view only (GET /api/v1/factory/rfqs); omitted elsewhere ──
    /** Buyer who posted the RFQ. */
    private String buyerName;
    private String buyerCountry;
    private String buyerAvatarUrl;
    /** This factory's current quotation, if it has quoted. */
    private BigDecimal myQuotePrice;
    private String myQuoteCurrency;
    private Integer myQuoteLeadTimeDays;
    private String myQuoteIncoterm;
    private String myQuoteNotes;
    private String myQuotedAt;
}