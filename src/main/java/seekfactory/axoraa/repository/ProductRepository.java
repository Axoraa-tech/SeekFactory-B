package seekfactory.axoraa.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Product;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {

    Optional<Product> findBySlug(String slug);

    List<Product> findByManufacturerIdAndIsActiveTrue(String manufacturerId);

    List<Product> findByCategoryIdAndIsActiveTrue(String categoryId);

    @Query("SELECT p FROM Product p WHERE p.isActive = true ORDER BY p.createdAt DESC")
    List<Product> findTrending(Pageable pageable);

    List<Product> findByManufacturerIdAndIdNot(String manufacturerId, String excludeId);
}