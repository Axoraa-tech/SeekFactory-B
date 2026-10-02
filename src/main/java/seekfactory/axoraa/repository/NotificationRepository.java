// NotificationRepository.java
package seekfactory.axoraa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Notification;
import java.util.List;
import org.springframework.data.repository.query.Param;
import seekfactory.axoraa.enums.NotificationType;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);
    long countByUserIdAndIsReadFalse(String userId);

    boolean existsByUserIdAndNotificationTypeAndReferenceIdAndIsReadFalse(String userId,
                                                                          NotificationType type,
                                                                          String referenceId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId "
            + "AND n.notificationType = :type AND n.referenceId = :referenceId AND n.isRead = false")
    int markReadByReference(@Param("userId") String userId,
                            @Param("type") NotificationType type,
                            @Param("referenceId") String referenceId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId")
    void markAllAsReadForUser(@Param("userId") String userId);
}