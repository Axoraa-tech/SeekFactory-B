package seekfactory.axoraa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.ProductSave;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductSaveRepository extends JpaRepository<ProductSave, String> {

    Optional<ProductSave> findByProductIdAndUserId(String productId, String userId);

    List<ProductSave> findByUserIdOrderByCreatedAtDesc(String userId);

    @Query("SELECT s.product.id FROM ProductSave s WHERE s.user.id = :userId AND s.product.id IN :productIds")
    List<String> findSavedProductIds(@Param("userId") String userId,
                                     @Param("productIds") Collection<String> productIds);
}
