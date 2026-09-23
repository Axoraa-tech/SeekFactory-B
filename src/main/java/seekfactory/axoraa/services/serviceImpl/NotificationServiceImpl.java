package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Response.notification.NotificationResponse;
import seekfactory.axoraa.entity.Notification;
import seekfactory.axoraa.repository.NotificationRepository;
import seekfactory.axoraa.services.services.NotificationService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages user notifications.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> listByUser(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    public void markAllAsRead(String userId) {
        notificationRepository.markAllAsReadForUser(userId);
        log.info("Marked all notifications as read for user: {}", userId);
    }

    @Override
    public void markAsRead(String id, String userId) {
        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getUser() != null && n.getUser().getId().equals(userId)) {
                n.setIsRead(true);
                notificationRepository.save(n);
                log.info("Marked notification {} as read for user: {}", id, userId);
            }
        });
    }

    @Override
    public void deleteNotification(String id, String userId) {
        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getUser() != null && n.getUser().getId().equals(userId)) {
                notificationRepository.delete(n);
                log.info("Deleted notification {} for user: {}", id, userId);
            }
        });
    }

    // ─── Private Helpers ──────────────────────────────────────

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .body(notification.getBody())
                .createdAt(notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : "")
                .read(notification.getIsRead() != null ? notification.getIsRead() : false)
                .build();
    }
}
