package seekfactory.axoraa.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.ProductLimitRequest;
import seekfactory.axoraa.enums.ProductLimitRequestStatus;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface ProductLimitRequestRepository extends JpaRepository<ProductLimitRequest, String> {

    Optional<ProductLimitRequest> findFirstByManufacturerIdOrderByCreatedAtDesc(String manufacturerId);

    boolean existsByManufacturerIdAndStatus(String manufacturerId, ProductLimitRequestStatus status);

    long countByStatus(ProductLimitRequestStatus status);

    /** Admin queue: status filter and a case-insensitive factory name / owner email search. */
    @EntityGraph(attributePaths = {"manufacturer", "manufacturer.user"})
    @Query(value = """
            SELECT r FROM ProductLimitRequest r JOIN r.manufacturer m LEFT JOIN m.user u
            WHERE r.status IN :statuses
              AND (LOWER(m.name) LIKE :pattern OR LOWER(COALESCE(u.email, '')) LIKE :pattern)
            ORDER BY r.createdAt DESC
            """, countQuery = """
            SELECT COUNT(r) FROM ProductLimitRequest r JOIN r.manufacturer m LEFT JOIN m.user u
            WHERE r.status IN :statuses
              AND (LOWER(m.name) LIKE :pattern OR LOWER(COALESCE(u.email, '')) LIKE :pattern)
            """)
    Page<ProductLimitRequest> search(@Param("statuses") Collection<ProductLimitRequestStatus> statuses,
                                     @Param("pattern") String pattern,
                                     Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ProductLimitRequest r WHERE r.id = :id")
    Optional<ProductLimitRequest> findByIdForUpdate(@Param("id") String id);
}
