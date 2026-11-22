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
import seekfactory.axoraa.dto.Response.settings.ExchangeRatesResponse;
import seekfactory.axoraa.dto.Response.settings.FeedShowcaseSettings;
import seekfactory.axoraa.services.services.PlatformSettingsService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@Tag(name = "Settings", description = "Admin-managed site configuration")
public class SettingsController {

    private final PlatformSettingsService platformSettingsService;
    private final seekfactory.axoraa.services.AdminAlertService adminAlertService;

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

    @GetMapping("/api/v1/settings/exchange-rates")
    @Operation(summary = "Public: display currency conversion rates (1 INR = rate units)")
    public ResponseEntity<ApiResponse<ExchangeRatesResponse>> getExchangeRates() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(ApiResponse.of(platformSettingsService.getExchangeRates()));
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

    // ---- Email alerts to admins (new sign-ups, content, payments) ----

    @GetMapping("/api/v1/admin/settings/alerts")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Admin: email alert settings")
    public ResponseEntity<ApiResponse<seekfactory.axoraa.services.AdminAlertService.Settings>> getAlerts() {
        return ResponseEntity.ok(ApiResponse.of(adminAlertService.getSettings()));
    }

    @PutMapping("/api/v1/admin/settings/alerts")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Admin: turn email alerts on/off, set recipients and events")
    public ResponseEntity<ApiResponse<seekfactory.axoraa.services.AdminAlertService.Settings>> updateAlerts(
            @RequestBody seekfactory.axoraa.services.AdminAlertService.Settings request) {
        return ResponseEntity.ok(ApiResponse.of(
                adminAlertService.updateSettings(request, SecurityUtils.getCurrentUserId()), "Email alerts saved"));
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/v1/admin/settings/alerts/test")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Admin: send a test alert to the saved recipients")
    public ResponseEntity<ApiResponse<java.util.Map<String, Integer>>> testAlerts() {
        int sent = adminAlertService.sendTest();
        return ResponseEntity.ok(ApiResponse.of(java.util.Map.of("sent", sent), "Test email sent"));
    }
}
