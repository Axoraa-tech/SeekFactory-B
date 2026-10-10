package seekfactory.axoraa.repository.Orders;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Orders.CartItem;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, String> {

    List<CartItem> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<CartItem> findByUserIdAndProductId(String userId, String productId);

    Optional<CartItem> findByIdAndUserId(String id, String userId);
}
