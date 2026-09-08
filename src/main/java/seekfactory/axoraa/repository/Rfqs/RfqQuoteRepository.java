// RfqQuoteRepository.java
package seekfactory.axoraa.repository.Rfqs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Rfqs.RfqQuote;

import java.util.List;

@Repository
public interface RfqQuoteRepository extends JpaRepository<RfqQuote, String> {

    List<RfqQuote> findByRfqIdOrderByCreatedAtDesc(String rfqId);

    List<RfqQuote> findByManufacturerIdOrderByCreatedAtDesc(String manufacturerId);
}