package seekfactory.axoraa.entity;

import jakarta.persistence.*;
import lombok.*;
import seekfactory.axoraa.enums.NotificationType;

/**
 * Platform notification delivered to a user.
 * Supports filtering by type (System, Quote, RFQ, Message, Follow).
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user_id", columnList = "user_id"),
        @Index(name = "idx_notifications_is_read", columnList = "is_read")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", length = 64, nullable = false)
    @Builder.Default
    private NotificationType notificationType = NotificationType.SYSTEM;

    @Column(name = "reference_id", length = 64)
    private String referenceId;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;
}