package seekfactory.axoraa.entity.Rfqs;


import jakarta.persistence.*;
import lombok.*;
import seekfactory.axoraa.entity.BaseEntity;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.enums.Currency;
import seekfactory.axoraa.enums.QuoteStatus;

import java.math.BigDecimal;

/**
 * A supplier's price quotation in response to a buyer's RFQ.
 */

@Entity
@Table(name = "rfq_quotes", indexes = { @Index(name = "idx_rfq_quotes_rfq_id",columnList = "rfq_id"), @Index(name = "idx_rfq_quotes_manufacturer_id", columnList = "manufacturer_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RfqQuote extends BaseEntity {


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rfq_id", nullable = false)
    private Rfq rfq;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturer_id", nullable = false)
    private Manufacturer manufacturer;

    @Column(name = "quote_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal quotePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 10, nullable = false)
    @Builder.Default
    private Currency currency = Currency.USD;

    @Column(name = "lead_time_days", nullable = false)
    private Integer leadTimeDays;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "attachment_url", columnDefinition = "TEXT")
    private String attachmentUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    @Builder.Default
    private QuoteStatus status = QuoteStatus.PENDING;
}
