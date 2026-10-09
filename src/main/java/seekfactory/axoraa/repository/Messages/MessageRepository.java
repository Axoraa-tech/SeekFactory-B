// MessageRepository.java
package seekfactory.axoraa.repository.Messages;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Messages.Message;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    /** Marks the other participant's unread messages in a conversation as read, in one statement. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Message m SET m.isRead = true WHERE m.conversation.id = :conversationId "
            + "AND m.sender.id <> :readerId AND (m.isRead = false OR m.isRead IS NULL)")
    int markReadFor(String conversationId, String readerId);
}