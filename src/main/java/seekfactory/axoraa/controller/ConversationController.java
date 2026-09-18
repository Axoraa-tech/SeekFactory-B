package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.message.ConversationResponse;
import seekfactory.axoraa.services.services.ConversationService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.util.List;

/**
 * B2B messaging / conversations — AUTHENTICATED only.
 *
 * Returns conversations enriched with manufacturer info,
 * matching the frontend MessageRepository.listRecent() contract.
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
}