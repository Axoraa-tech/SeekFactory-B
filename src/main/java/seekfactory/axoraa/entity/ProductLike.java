package seekfactory.axoraa.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_likes", uniqueConstraints = @UniqueConstraint(name = "uq_product_likes_product_user", columnNames = {"product_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductLike extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
