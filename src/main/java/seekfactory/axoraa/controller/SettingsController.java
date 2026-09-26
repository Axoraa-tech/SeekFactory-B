package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.settings.FeedShowcaseSettings;
import seekfactory.axoraa.services.services.PlatformSettingsService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@Tag(name = "Settings", description = "Admin-managed site configuration")
public class SettingsController {

    private final PlatformSettingsService platformSettingsService;

    @GetMapping("/api/v1/settings/feed-showcase")
    @Operation(summary = "Public: how the website home feed showcases seeks")
    public ResponseEntity<ApiResponse<FeedShowcaseSettings>> getFeedShowcase() {
        FeedShowcaseSettings s = platformSettingsService.getFeedShowcase();
        // Do not leak which admin changed it to the public
        FeedShowcaseSettings publicView = new FeedShowcaseSettings(s.mode(), s.autoplay(), s.showProfile(), s.showPhotos(), s.updatedAt(), null);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePublic())
                .body(ApiResponse.of(publicView));
    }

    @GetMapping("/api/v1/admin/settings/feed-showcase")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Admin: current feed showcase settings with audit info")
    public ResponseEntity<ApiResponse<FeedShowcaseSettings>> getFeedShowcaseForAdmin() {
        return ResponseEntity.ok(ApiResponse.of(platformSettingsService.getFeedShowcase()));
    }

    @PutMapping("/api/v1/admin/settings/feed-showcase")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Admin: publish a new feed showcase layout")
    public ResponseEntity<ApiResponse<FeedShowcaseSettings>> updateFeedShowcase(@Valid @RequestBody FeedShowcaseSettings request) {
        FeedShowcaseSettings saved = platformSettingsService.updateFeedShowcase(request, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.of(saved, "Feed showcase published"));
    }
}
