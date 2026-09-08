// ConversationRepository.java
package seekfactory.axoraa.repository.Messages;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Messages.Conversation;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {

    List<Conversation> findByBuyerIdOrderByLastMessageAtDesc(String buyerId);

    Optional<Conversation> findByBuyerIdAndManufacturerId(String buyerId, String manufacturerId);
}