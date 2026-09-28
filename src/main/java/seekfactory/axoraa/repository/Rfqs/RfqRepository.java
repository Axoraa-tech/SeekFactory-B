// RfqRepository.java
package seekfactory.axoraa.repository.Rfqs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Rfqs.Rfq;

import java.util.List;
import java.util.Optional;

@Repository
public interface RfqRepository extends JpaRepository<Rfq, String> {

    long countByStatus(seekfactory.axoraa.enums.RfqStatus status);

    List<Rfq> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Rfq> findByCategoryIdInOrderByCreatedAtDesc(List<String> categoryIds);

    /** RFQs in any of the given categories, plus uncategorised RFQs that every factory may quote on. */
    List<Rfq> findByCategoryIdInOrCategoryIsNullOrderByCreatedAtDesc(List<String> categoryIds);

    Optional<Rfq> findByIdAndUserId(String id, String userId);

    List<Rfq> findAllByOrderByCreatedAtDesc();

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(r.referenceNumber, 10) AS int)), 0) FROM Rfq r WHERE r.referenceNumber LIKE :yearPrefix")
    int findMaxSequenceForYear(String yearPrefix);
}