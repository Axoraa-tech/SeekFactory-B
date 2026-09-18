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

import java.util.List;

/**
 * Video reels feed — PUBLIC access.
 *
 * Returns a list of FeedItems (reel + manufacturer + primary product slug)
 * filtered by the active feed tab ("for-you" or "following").
 *
 * Both web and mobile frontends call: GET /api/v1/feed?tab=for-you
 */
@RestController
@RequestMapping("/api/v1/feed")
@RequiredArgsConstructor
@Tag(name = "Feed", description = "Video reels discovery feed")
public class FeedController {

    private final ReelService reelService;

    @GetMapping
    @Operation(summary = "Get video reels feed by tab")
    public ResponseEntity<ApiResponse<List<FeedItemResponse>>> getFeed(
            @RequestParam(defaultValue = "for-you") String tab) {

        // Map frontend tab string to enum
        FeedTab feedTab = switch (tab.toLowerCase()) {
            case "following" -> FeedTab.FOLLOWING;
            default -> FeedTab.FOR_YOU;
        };

        List<FeedItemResponse> feed = reelService.getFeed(feedTab);
        return ResponseEntity.ok(ApiResponse.of(feed));
    }
}