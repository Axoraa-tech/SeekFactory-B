package seekfactory.axoraa.entity.Rfqs;


import jakarta.persistence.*;
import lombok.*;
import seekfactory.axoraa.entity.BaseEntity;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.enums.Currency;
import seekfactory.axoraa.enums.Incoterm;
import seekfactory.axoraa.enums.RfqStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * A Request for Quotation — buyer's formal purchasing inquiry.
 *
 * Lifecycle: SUBMITTED → REVIEWING → QUOTING → QUOTED → ACCEPTED → IN_PRODUCTION → COMPLETED
 * Each RFQ generates a unique reference_number (e.g., "RFQ-2026-000142") for tracking.
 */

@Entity
@Table(name = "rfqs", indexes = { @Index(name = "idx_rfqs_user_id", columnList = "user_id"), @Index(name = "idx_rfqs_status", columnList = "status")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rfq extends BaseEntity {

    @Column(name = "reference_number", length = 64, unique = true, nullable = false)
    private String referenceNumber;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "quantity", length = 100, nullable = false)
    private String quantity;

    @Column(name = "unit", length = 50, nullable = false)
    @Builder.Default
    private String unit = "Pieces";

    @Column(name = "target_price", length = 100)
    private String targetPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 10, nullable = false)
    @Builder.Default
    private Currency currency = Currency.INR;

    @Enumerated(EnumType.STRING)
    @Column(name = "incoterm", length = 20, nullable = false)
    @Builder.Default
    private Incoterm incoterm = Incoterm.FOB;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "details", nullable = false, columnDefinition = "TEXT")
    private String details;

    @Column(name = "attachment_name")
    private String attachmentName;

    @Column(name = "attachment_size", length = 50)
    private String attachmentSize;

    @Column(name = "attachment_url", columnDefinition = "TEXT")
    private String attachmentUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    @Builder.Default
    private RfqStatus status = RfqStatus.SUBMITTED;



    // ─── Quotes from Suppliers

    @OneToMany(mappedBy = "rfq", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Rfq> quotes = new ArrayList<>();
}
