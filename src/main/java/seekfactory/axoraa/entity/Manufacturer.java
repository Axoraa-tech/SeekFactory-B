package seekfactory.axoraa.entity;


import jakarta.persistence.*;
import lombok.*;

import seekfactory.axoraa.enums.VerificationStatus;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a verified manufacturing plant / factory on the platform.
 *
 * Each manufacturer is linked 1:1 to a User with ROLE_SUPPLIER.
 * Has many-to-many relationship with Categories (the types of machinery they produce).
 * Export countries stored as an @ElementCollection for simple string storage.
 */

@Entity
@Table(name = "manufacturers", indexes = { @Index(name = "idx_manufacturers_slug", columnList = "slug"), @Index(name = "idx_manufacturers_verified", columnList = "verified"),
        @Index(name = "idx_manufacturers_verification_status", columnList = "verification_status")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Manufacturer extends BaseEntity{

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(name = "slug", length = 128, unique = true, nullable = false)
    private String slug;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "cover_url", columnDefinition = "TEXT")
    private String coverUrl;

    @Column(name = "country", length = 100, nullable = false)
    private String country;

    @Column(name = "location", nullable = false)
    private String location;

    @Column(name = "verified", nullable = false)
    @Builder.Default
    private Boolean verified = false;

    @Column(name = "premium", nullable = false)
    @Builder.Default
    private Boolean premium = false;

    @Column(name = "years_established", nullable = false)
    @Builder.Default
    private Integer yearsEstablished = 2000;

    @Column(name = "factory_size", length = 100)
    private String factorySize;

    @Column(name = "employees", length = 100)
    private String employees;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "follower_count", nullable = false)
    @Builder.Default
    private Integer followerCount = 0;

    @Column(name = "chairman_name")
    private String chairmanName;

    // ─── Admin review
    //
    // `verified` remains the badge/visibility flag the rest of the app reads;
    // `verificationStatus` carries the review lifecycle around it, so a rejected
    // factory is distinguishable from one that has simply not been looked at yet.

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 20, nullable = false)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    /** Id of the admin who approved or rejected; null while pending. */
    @Column(name = "reviewed_by", length = 64)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** Why the application was declined. Shown to the manufacturer. */
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // ─── Verification submission
    //
    // Details the factory supplies for review. Documents are not stored yet;
    // there is no upload pipeline in the platform.

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "company_reg_number", length = 120)
    private String companyRegNumber;

    @Column(name = "tax_id", length = 120)
    private String taxId;

    @Column(name = "registration_date", length = 40)
    private String registrationDate;

    @Column(name = "factory_address", columnDefinition = "TEXT")
    private String factoryAddress;

    @ElementCollection
    @CollectionTable(name = "manufacturer_certifications", joinColumns = @JoinColumn(name = "manufacturer_id"))
    @Column(name = "certification")
    @Builder.Default
    private Set<String> certifications = new HashSet<>();

    // ─── Export Countries (ElementCollection)

    @ElementCollection
    @CollectionTable(name = "manufacturer_export_countries", joinColumns = @JoinColumn(name = "manufacturer_id"))
    @Column(name = "country_name")
    @Builder.Default
    private Set<String> exportCountries = new HashSet<>();

    // ─── Category Associations (ManyToMany)

    @ManyToMany
    @JoinTable(name = "manufacturer_categories", joinColumns = @JoinColumn(name = "manufacturer_id"), inverseJoinColumns = @JoinColumn(name = "category_id"))
    @Builder.Default
    private Set<Category> categories = new HashSet<>();

    // ─── Products

    // ─── Subscription Plan
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private SubscriptionPlan subscriptionPlan;
}
