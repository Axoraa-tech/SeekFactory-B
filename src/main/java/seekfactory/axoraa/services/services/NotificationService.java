package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.notification.NotificationResponse;

import java.util.List;

public interface NotificationService {

    List<NotificationResponse> listByUser(String userId);

    long unreadCount(String userId);

    void markAllAsRead(String userId);

    void markAsRead(String id, String userId);

    void deleteNotification(String id, String userId);
}
