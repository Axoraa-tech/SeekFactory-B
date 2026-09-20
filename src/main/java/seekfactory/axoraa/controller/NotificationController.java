package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.notification.NotificationResponse;
import seekfactory.axoraa.services.services.NotificationService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.util.List;
import java.util.Map;

/**
 * User notifications — AUTHENTICATED only.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User activity notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List all notifications for current user")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> list() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(notificationService.listByUser(userId)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount() {
        String userId = SecurityUtils.getCurrentUserId();
        long count = notificationService.unreadCount(userId);
        return ResponseEntity.ok(ApiResponse.of(Map.of("count", count)));
    }

    @PutMapping("/mark-read")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead() {
        String userId = SecurityUtils.getCurrentUserId();
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.ok("All notifications marked as read"));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark single notification as read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        notificationService.markAsRead(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Notification marked as read"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete single notification")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        notificationService.deleteNotification(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Notification deleted"));
    }
}
