package seekfactory.axoraa.entity;


import jakarta.persistence.*;
import lombok.*;

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
@Table(name = "manufacturers", indexes = { @Index(name = "idx_manufacturers_slug", columnList = "slug"), @Index(name = "idx_manufacturers_verified", columnList = "verified")})
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
