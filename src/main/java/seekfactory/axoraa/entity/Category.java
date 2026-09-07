package seekfactory.axoraa.entity;


import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Hierarchical machinery taxonomy node.
 *
 * Root categories have parentId = null.
 * Child categories reference their parent via self-join.
 * The frontend uses 20 specific icon keys for visual representation.
 *
 * Example hierarchy:
 *   Agriculture (root, icon="agriculture")
 *   ├── Harvesters (child)
 *   ├── Tractors (child)
 *   └── Seeders (child)
 */

@Entity
@Table(name = "categories", indexes = { @Index(name = "idx_categories_slug", columnList = "slug"), @Index(name = "idx_categories_parent_id", columnList = "parent_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category extends BaseEntity{

    @Column(name = "slug", length = 128, unique = true, nullable = false)
    private String slug;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "icon", length = 64, nullable = false)
    @Builder.Default
    private String icon = "other";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Category> children = new ArrayList<>();

    @Column(name = "listing_count", nullable = false)
    @Builder.Default
    private Integer listingCount = 0;
}
