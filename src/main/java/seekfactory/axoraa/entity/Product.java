package seekfactory.axoraa.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * A manufactured product listed on the marketplace.
 *
 * The `specs` field uses PostgreSQL JSONB to store dynamic key-value
 * technical specifications (e.g., "Tolerance" → "±0.005mm",
 * "Material" → "SS304", "LeadTime" → "15 days").
 *
 * This avoids rigid column definitions for specs that vary wildly
 * across product categories.
 */

@Entity
@Table(name = "products", indexes = { @Index(name = "idx_product_slug", columnList = "slug"), @Index(name = "idx_products_manufacturer_id", columnList = "manufacturer_id"), @Index(name = "idx_products_category_id", columnList = "category_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity{

    @Column(name = "slug", length = 128, unique = true, nullable = false)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturer_id", nullable = false)
    private Manufacturer manufacturer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_inr", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal priceInr = BigDecimal.ZERO;

    @Column(name = "unit", length = 50, nullable = false)
    @Builder.Default
    private String unit = "piece";

    @Column(name = "moq", length = 100, nullable = false)
    @Builder.Default
    private String moq = "1 piece";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specs", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> specs = new HashMap<>();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
