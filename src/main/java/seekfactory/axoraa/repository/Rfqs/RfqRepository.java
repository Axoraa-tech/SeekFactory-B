// RfqRepository.java
package seekfactory.axoraa.repository.Rfqs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Rfqs.Rfq;

import java.util.List;

@Repository
public interface RfqRepository extends JpaRepository<Rfq, String> {

    long countByStatus(seekfactory.axoraa.enums.RfqStatus status);

    List<Rfq> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Rfq> findByCategoryIdInOrderByCreatedAtDesc(List<String> categoryIds);

    List<Rfq> findAllByOrderByCreatedAtDesc();

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(r.referenceNumber, 10) AS int)), 0) FROM Rfq r WHERE r.referenceNumber LIKE :yearPrefix")
    int findMaxSequenceForYear(String yearPrefix);
}