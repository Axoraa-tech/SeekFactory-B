package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Request.message.MessageSendRequest;
import seekfactory.axoraa.dto.Request.message.StartConversationRequest;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.message.ConversationResponse;
import seekfactory.axoraa.dto.Response.message.MessageResponse;
import seekfactory.axoraa.services.services.ConversationService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.util.List;

/**
 * B2B messaging / conversations — AUTHENTICATED only.
 */
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@Tag(name = "Messages", description = "B2B buyer-supplier messaging")
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    @Operation(summary = "List recent conversations with manufacturers")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> listRecent(
            @RequestParam(defaultValue = "20") int limit) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(
                conversationService.listRecent(userId, limit)));
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
