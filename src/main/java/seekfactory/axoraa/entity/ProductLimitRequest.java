package seekfactory.axoraa.entity;

import jakarta.persistence.*;
import lombok.*;
import seekfactory.axoraa.enums.ProductLimitRequestStatus;

import java.time.Instant;

/**
 * A manufacturer asking for a higher product limit. Handled manually by an admin:
 * no price is quoted and no payment is taken or recorded.
 */
@Entity
@Table(name = "product_limit_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductLimitRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manufacturer_id", nullable = false)
    private Manufacturer manufacturer;

    /** Live product count when the request was made. */
    @Column(name = "current_count", nullable = false)
    private Integer currentCount;

    /** Limit when the request was made. */
    @Column(name = "current_limit", nullable = false)
    private Integer currentLimit;

    @Column(name = "requested_limit", nullable = false)
    private Integer requestedLimit;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private ProductLimitRequestStatus status = ProductLimitRequestStatus.PENDING;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "reviewed_by", length = 64)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
