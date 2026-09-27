package seekfactory.axoraa.entity.Orders;

import jakarta.persistence.*;
import lombok.*;
import seekfactory.axoraa.entity.BaseEntity;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.entity.User;

/**
 * One product line in a buyer's cart. Unique per (user, product): adding again raises the quantity.
 */
@Entity
@Table(name = "cart_items", uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "product_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;
}
