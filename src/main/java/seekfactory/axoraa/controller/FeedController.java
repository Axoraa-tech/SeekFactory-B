package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.reel.FeedItemResponse;
import seekfactory.axoraa.enums.FeedTab;
import seekfactory.axoraa.services.services.ReelService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.util.List;
import java.util.Map;

/**
 * Video reels feed — PUBLIC discovery & authenticated interactions.
 */
@RestController
@RequestMapping("/api/v1/feed")
@RequiredArgsConstructor
@Tag(name = "Feed", description = "Video reels discovery feed")
public class FeedController {

    private final ReelService reelService;

    @GetMapping
    @Operation(summary = "Get video reels feed by tab (following = seeks from factories the viewer follows)")
    public ResponseEntity<ApiResponse<List<FeedItemResponse>>> getFeed(
            @RequestParam(defaultValue = "for-you") String tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        FeedTab feedTab = switch (tab.toLowerCase()) {
            case "following" -> FeedTab.FOLLOWING;
            default -> FeedTab.FOR_YOU;
        };
        String viewerId = SecurityUtils.findCurrentUserId().orElse(null);
        return ResponseEntity.ok(ApiResponse.of(reelService.getFeed(feedTab, viewerId, page, size)));
    }

    @PostMapping("/{id}/like")
    @Operation(summary = "Toggle like on a reel")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleLike(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(reelService.toggleLike(id, userId)));
    }

    @PostMapping("/{id}/save")
    @Operation(summary = "Toggle save/bookmark on a reel")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleSave(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(reelService.toggleSave(id, userId)));
    }

    @PostMapping("/{id}/share")
    @Operation(summary = "Count a share of a reel (guests included)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> share(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(reelService.recordShare(id)));
    }
}
