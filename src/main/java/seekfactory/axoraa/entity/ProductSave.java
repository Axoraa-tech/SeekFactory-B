package seekfactory.axoraa.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A user's saved (wishlisted) product. Saved seeks are tracked separately in ReelSave.
 */
@Entity
@Table(name = "product_saves", uniqueConstraints = {@UniqueConstraint(columnNames = {"product_id", "user_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSave extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
