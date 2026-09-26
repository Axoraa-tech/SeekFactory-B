package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import seekfactory.axoraa.dto.Request.analytics.ViewEventRequest;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.services.services.AnalyticsService;

import java.util.Map;

/**
 * Public view tracking (guests included) feeding the seller dashboard KPIs.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "View Tracking", description = "Record seek impressions and product views for seller analytics")
public class ViewTrackingController {

    private final AnalyticsService analyticsService;

    @PostMapping("/api/v1/feed/{reelId}/view")
    @Operation(summary = "Record a seek (reel) impression")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> recordReelView(
            @PathVariable String reelId,
            @Valid @RequestBody(required = false) ViewEventRequest request) {
        boolean counted = analyticsService.recordReelView(reelId, request != null ? request.getViewerId() : null);
        return ResponseEntity.ok(ApiResponse.of(Map.of("counted", counted)));
    }

    @PostMapping("/api/v1/products/{productId}/view")
    @Operation(summary = "Record a product detail view")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> recordProductView(
            @PathVariable String productId,
            @Valid @RequestBody(required = false) ViewEventRequest request) {
        boolean counted = analyticsService.recordProductView(productId, request != null ? request.getViewerId() : null);
        return ResponseEntity.ok(ApiResponse.of(Map.of("counted", counted)));
    }
}
