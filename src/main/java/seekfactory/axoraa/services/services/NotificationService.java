package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.notification.NotificationResponse;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.NotificationType;

import java.util.List;

public interface NotificationService {

    List<NotificationResponse> listByUser(String userId);

    long unreadCount(String userId);

    void markAllAsRead(String userId);

    void markAsRead(String id, String userId);

    void deleteNotification(String id, String userId);

    /** Creates a notification for the user. A null user (e.g. a factory with no account) is ignored. */
    void notify(User user, NotificationType type, String title, String body, String referenceId);

    /**
     * Like notify, but skipped while the user still has an unread notification of the same type
     * about the same reference, so a busy chat produces one alert rather than one per message.
     */
    void notifyOnce(User user, NotificationType type, String title, String body, String referenceId);
}
