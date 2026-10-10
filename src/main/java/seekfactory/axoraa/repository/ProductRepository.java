package seekfactory.axoraa.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {

    Optional<Product> findBySlug(String slug);

    List<Product> findByCategoryIdInAndIsActiveTrueAndListedTrueAndManufacturerVerifiedTrueOrderByCreatedAtDesc(Collection<String> categoryIds);

    /**
     * Buyer search. Pass "%" to match everything; pass allCategories=true to skip the category filter
     * (categoryIds must still be non-empty for the query to render).
     */
    @Query("""
            SELECT p FROM Product p
            WHERE p.isActive = true AND p.listed = true AND p.manufacturer.verified = true
              AND (LOWER(p.name) LIKE :pattern OR LOWER(COALESCE(p.description, '')) LIKE :pattern
                   OR LOWER(p.manufacturer.name) LIKE :pattern)
              AND (:allCategories = true OR p.category.id IN :categoryIds)
            ORDER BY p.createdAt DESC
            """)
    List<Product> search(@Param("pattern") String pattern,
                         @Param("allCategories") boolean allCategories,
                         @Param("categoryIds") Collection<String> categoryIds,
                         Pageable pageable);

    List<Product> findByManufacturerIdAndIsActiveTrue(String manufacturerId);

    /** Buyer-facing: not deleted and not paused by the seller. */
    List<Product> findByManufacturerIdAndIsActiveTrueAndListedTrue(String manufacturerId);

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.listed = true AND p.manufacturer.verified = true ORDER BY p.createdAt DESC")
    List<Product> findTrending(Pageable pageable);

    List<Product> findByManufacturerIdAndIdNot(String manufacturerId, String excludeId);
}