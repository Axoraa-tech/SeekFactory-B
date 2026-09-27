package seekfactory.axoraa.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Manufacturer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ManufacturerRepository extends JpaRepository<Manufacturer, String> {

    Optional<Manufacturer> findBySlug(String slug);

    @Query("""
            SELECT DISTINCT m FROM Manufacturer m LEFT JOIN m.categories c
            WHERE m.verified = true
              AND (LOWER(m.name) LIKE :pattern OR LOWER(m.location) LIKE :pattern
                   OR LOWER(m.country) LIKE :pattern OR LOWER(COALESCE(m.description, '')) LIKE :pattern)
              AND (:allCategories = true OR c.id IN :categoryIds)
            ORDER BY m.followerCount DESC
            """)
    List<Manufacturer> search(@Param("pattern") String pattern,
                              @Param("allCategories") boolean allCategories,
                              @Param("categoryIds") Collection<String> categoryIds,
                              Pageable pageable);

    Optional<Manufacturer> findByUserId(String userId);

    long countByVerifiedTrue();

    List<Manufacturer> findByVerifiedTrueOrderByFollowerCountDesc();

    List<Manufacturer> findByPremiumTrue();
}