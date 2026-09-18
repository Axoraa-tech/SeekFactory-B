package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Request.comment.CommentCreateRequest;
import seekfactory.axoraa.dto.Request.comment.ReplyCreateRequest;
import seekfactory.axoraa.dto.Response.comment.CommentReplyResponse;
import seekfactory.axoraa.dto.Response.comment.CommentResponse;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.services.services.CommentService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.util.List;

/**
 * Reel comments and replies.
 *
 * Reading comments is PUBLIC (anyone can view).
 * Writing comments/replies requires AUTHENTICATION.
 *
 * URL pattern follows REST conventions:
 * - GET  /api/v1/reels/{reelId}/comments      → list comments (public)
 * - POST /api/v1/reels/{reelId}/comments      → add comment (auth)
 * - POST /api/v1/comments/{commentId}/replies  → add reply (auth)
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Reel comments and threaded replies")
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/api/v1/reels/{reelId}/comments")
    @Operation(summary = "List comments for a reel (public)")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> listByReel(
            @PathVariable String reelId) {
        return ResponseEntity.ok(ApiResponse.of(commentService.listByReelId(reelId)));
    }

    @PostMapping("/api/v1/reels/{reelId}/comments")
    @Operation(summary = "Add a comment to a reel (requires authentication)")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @PathVariable String reelId,
            @Valid @RequestBody CommentCreateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        CommentResponse comment = commentService.addComment(reelId, userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of(comment, "Comment added"));
    }

    @PostMapping("/api/v1/comments/{commentId}/replies")
    @Operation(summary = "Reply to a comment (requires authentication)")
    public ResponseEntity<ApiResponse<CommentReplyResponse>> addReply(
            @PathVariable String commentId,
            @Valid @RequestBody ReplyCreateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        CommentReplyResponse reply = commentService.addReply(commentId, userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of(reply, "Reply added"));
    }
}