// ConversationRepository.java
package seekfactory.axoraa.repository.Messages;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Messages.Conversation;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {

    List<Conversation> findByBuyerIdOrderByLastMessageAtDesc(String buyerId);

    List<Conversation> findByManufacturerUserIdOrderByLastMessageAtDesc(String userId);

    List<Conversation> findByManufacturerIdOrderByLastMessageAtDesc(String manufacturerId);

    List<Conversation> findByBuyerIdOrderByLastMessageAtDesc(String buyerId, Pageable pageable);

    List<Conversation> findByManufacturerIdOrderByLastMessageAtDesc(String manufacturerId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(c.unreadCountBuyer), 0) FROM Conversation c WHERE c.buyer.id = :buyerId")
    long sumUnreadForBuyer(String buyerId);

    @Query("SELECT COALESCE(SUM(c.unreadCountSupplier), 0) FROM Conversation c WHERE c.manufacturer.id = :manufacturerId")
    long sumUnreadForManufacturer(String manufacturerId);

    Optional<Conversation> findByBuyerIdAndManufacturerId(String buyerId, String manufacturerId);
}