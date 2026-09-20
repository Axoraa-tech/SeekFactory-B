package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.pricing.SubscriptionPlanResponse;
import seekfactory.axoraa.services.services.PricingService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
@Tag(name = "Pricing", description = "Factory subscription tiers and upgrade management")
public class PricingController {

    private final PricingService pricingService;

    @GetMapping("/plans")
    @Operation(summary = "Get all available subscription plans for manufacturers")
    public ResponseEntity<ApiResponse<List<SubscriptionPlanResponse>>> getAllPlans() {
        List<SubscriptionPlanResponse> plans = pricingService.getAllPlans();
        return ResponseEntity.ok(ApiResponse.of(plans));
    }

    @PutMapping("/admin/manufacturers/{manufacturerId}/subscription/{subscriptionId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Admin only: Upgrade or downgrade a manufacturer's subscription tier")
    public ResponseEntity<ApiResponse<Void>> updateSubscription(
            @PathVariable String manufacturerId,
            @PathVariable String subscriptionId) {
        pricingService.upgradeManufacturerSubscription(manufacturerId, subscriptionId);
        return ResponseEntity.ok(ApiResponse.ok("Subscription updated successfully"));
    }
}
