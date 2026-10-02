package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import seekfactory.axoraa.dto.Request.message.MessageSendRequest;
import seekfactory.axoraa.dto.Request.message.StartConversationRequest;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.message.ConversationResponse;
import seekfactory.axoraa.dto.Response.message.MessageResponse;
import seekfactory.axoraa.services.services.ConversationService;
import seekfactory.axoraa.services.services.SseService;
import seekfactory.axoraa.services.services.MediaStorageService;
import seekfactory.axoraa.dto.Response.message.AttachmentUploadResponse;
import seekfactory.axoraa.dto.Response.order.OrderResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.util.concurrent.TimeUnit;
import seekfactory.axoraa.utils.SecurityUtils;

import java.net.URI;
import java.util.Optional;
import java.util.List;
import java.util.Map;

/**
 * B2B messaging / conversations — AUTHENTICATED only.
 */
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@Tag(name = "Messages", description = "B2B buyer-supplier messaging")
public class ConversationController {

    private final ConversationService conversationService;
    private final SseService sseService;
    private final MediaStorageService mediaStorageService;

    @GetMapping(value = "/{id}/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to real-time message stream via Server-Sent Events (SSE)")
    public SseEmitter streamMessages(@PathVariable String id) {
        // Only the buyer and the factory of this conversation may listen to it
        String userId = SecurityUtils.getCurrentUserId();
        conversationService.assertParticipant(id, userId);
        return sseService.subscribe(id, userId);
    }

    @GetMapping("/{id}/orders")
    @Operation(summary = "Orders between this conversation's buyer and factory (for message context)")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> conversationOrders(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(
                conversationService.listConversationOrders(id, SecurityUtils.getCurrentUserId())));
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an image or PDF to attach to a message (participants only)")
    public ResponseEntity<ApiResponse<AttachmentUploadResponse>> uploadAttachment(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file) {
        AttachmentUploadResponse response =
                conversationService.uploadAttachment(id, SecurityUtils.getCurrentUserId(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Attachment uploaded"));
    }

    @GetMapping("/{id}/attachments/{key}")
    @Operation(summary = "Download a message attachment (participants only)")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable String id, @PathVariable String key) {
        Resource file = conversationService.loadAttachment(id, SecurityUtils.getCurrentUserId(), key);
        // Bucket storage: the participant check above passed, so hand out a short-lived signed link
        Optional<String> signed = mediaStorageService.privateUrl(id, key);
        if (signed.isPresent()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(signed.get()))
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
        String contentType = mediaStorageService.contentTypeOf(key);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff");
        if (!"application/pdf".equals(contentType)) {
            // Images are rendered from our origin; never let them run script. (Not applied to
            // PDFs: Chrome's PDF viewer refuses to render sandboxed documents.)
            response.header("Content-Security-Policy", "sandbox; default-src 'none'; img-src 'self'; style-src 'unsafe-inline'");
        }
        return response.body(file);
    }

    @GetMapping
    @Operation(summary = "List recent conversations with manufacturers")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> listRecent(
            @RequestParam(defaultValue = "20") int limit) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(
                conversationService.listRecent(userId, limit)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Unread messages across all of the viewer's conversations")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(Map.of("count", conversationService.unreadCount(userId))));
    }

    @PostMapping
    @Operation(summary = "Start or retrieve an existing conversation with a manufacturer")
    public ResponseEntity<ApiResponse<ConversationResponse>> startConversation(
            @Valid @RequestBody StartConversationRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        ConversationResponse response = conversationService.getOrCreateConversation(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Conversation ready"));
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Get message history for a conversation")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getMessages(
            @PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(conversationService.getMessages(id, userId)));
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Send a message in a conversation")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @PathVariable String id,
            @Valid @RequestBody MessageSendRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        MessageResponse response = conversationService.sendMessage(id, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Message sent"));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark conversation messages as read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        conversationService.markAsRead(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Conversation marked as read"));
    }
}
