package seekfactory.axoraa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import seekfactory.axoraa.entity.ProductLike;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductLikeRepository extends JpaRepository<ProductLike, String> {
    Optional<ProductLike> findByProductIdAndUserId(String productId, String userId);

    long countByProductId(String productId);

    @Query("SELECT l.product.id, COUNT(l) FROM ProductLike l WHERE l.product.id IN :productIds GROUP BY l.product.id")
    List<Object[]> countForProducts(@Param("productIds") Collection<String> productIds);

    @Query("SELECT l.product.id FROM ProductLike l WHERE l.user.id = :userId AND l.product.id IN :productIds")
    List<String> findLikedProductIds(@Param("userId") String userId, @Param("productIds") Collection<String> productIds);
}
