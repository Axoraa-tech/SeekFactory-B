package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Request.plan.ProductLimitDecisionRequest;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.Page;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.plan.AdminProductLimitRequestRow;
import seekfactory.axoraa.services.serviceImpl.ProductLimitService;
import seekfactory.axoraa.utils.SecurityUtils;

/**
 * Manual handling of manufacturer product-limit upgrade requests. No pricing or payment:
 * the admin contacts the factory and raises (or declines) the limit by hand.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin", description = "Platform administration (ROLE_ADMIN only)")
public class AdminProductLimitController {

    private final ProductLimitService productLimitService;

    @GetMapping("/product-limit-requests")
    @Operation(summary = "Product limit upgrade requests (status: PENDING, APPROVED, REJECTED or ALL)")
    public ResponseEntity<ApiResponse<Page<AdminProductLimitRequestRow>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(productLimitService.list(status, q, page, size)));
    }

    @PutMapping("/product-limit-requests/{id}/approve")
    @Operation(summary = "Approve: set the factory's limit to approvedLimit (defaults to the requested limit)")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable String id, @Valid @RequestBody(required = false) ProductLimitDecisionRequest body) {
        productLimitService.approve(id, body, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Product limit updated"));
    }

    @PutMapping("/product-limit-requests/{id}/reject")
    @Operation(summary = "Decline a request (note required)")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable String id, @Valid @RequestBody ProductLimitDecisionRequest body) {
        productLimitService.reject(id, body, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Request declined"));
    }

    @PutMapping("/manufacturers/{id}/product-limit")
    @Operation(summary = "Set a manufacturer's product limit directly (existing products are never removed)")
    public ResponseEntity<ApiResponse<Void>> setLimit(@PathVariable String id, @RequestParam int limit) {
        productLimitService.setLimit(id, limit, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Product limit updated"));
    }
}
