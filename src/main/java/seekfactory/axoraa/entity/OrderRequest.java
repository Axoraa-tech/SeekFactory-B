package seekfactory.axoraa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import seekfactory.axoraa.enums.OrderStatus;
import seekfactory.axoraa.entity.Rfqs.RfqQuote;
import seekfactory.axoraa.enums.Currency;
import seekfactory.axoraa.enums.OrderSource;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A buyer's order request for a product (see V10). Product details are snapshotted at
 * order time so the order stays accurate if the listing later changes or is removed.
 */
@Entity
@Table(name = "order_requests", indexes = {
        @Index(name = "idx_order_requests_mfr_created", columnList = "manufacturer_id, created_at"),
        @Index(name = "idx_order_requests_buyer_created", columnList = "buyer_id, created_at")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequest extends BaseEntity {

    @Column(name = "reference_number", length = 32, nullable = false, unique = true)
    private String referenceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturer_id", nullable = false)
    private Manufacturer manufacturer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "product_slug")
    private String productSlug;

    @Column(name = "product_image_url", columnDefinition = "TEXT")
    private String productImageUrl;

    @Column(name = "unit_price_inr", precision = 14, scale = 2)
    private BigDecimal unitPriceInr;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "buyer_note", columnDefinition = "TEXT")
    private String buyerNote;

    @Column(name = "seller_note", columnDefinition = "TEXT")
    private String sellerNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "status_updated_at", nullable = false)
    @Builder.Default
    private Instant statusUpdatedAt = Instant.now();

    // ─── Buyer-side details (V15) ─────────────────────────────

    /** Where the request came from: a product page, the cart, or an accepted RFQ quote. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 16, nullable = false)
    @Builder.Default
    private OrderSource source = OrderSource.DIRECT;

    /** The accepted quote, for RFQ_QUOTE orders. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rfq_quote_id")
    private RfqQuote rfqQuote;

    /** Currency of {@link #quotedTotal}; listing prices are always INR. */
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 3, nullable = false)
    @Builder.Default
    private Currency currency = Currency.INR;

    /** Total the factory quoted, for RFQ_QUOTE orders (may be in a non-INR currency). */
    @Column(name = "quoted_total", precision = 16, scale = 2)
    private BigDecimal quotedTotal;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "delivery_address", columnDefinition = "TEXT")
    private String deliveryAddress;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
