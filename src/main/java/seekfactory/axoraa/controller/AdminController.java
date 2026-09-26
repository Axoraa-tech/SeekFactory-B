package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.ManufacturerDetail;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.ManufacturerRow;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.Page;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.PlanRequest;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.PlanRow;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.RfqRow;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.RfqStatusRequest;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.UserRow;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.services.services.AdminManagementService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.util.List;

/**
 * Admin-only endpoints — requires ROLE_ADMIN authority.
 *
 * Provides platform administration capabilities:
 * - Search, filter and page users, manufacturers and RFQs
 * - Activate/deactivate user accounts
 * - Verify/unverify manufacturers and assign subscription plans
 * - Move RFQs through their status pipeline
 * - Manage subscription plans
 *
 * Double-protected: SecurityConfig restricts /api/v1/admin/** to ROLE_ADMIN,
 * AND each method has @PreAuthorize as defense-in-depth.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin", description = "Platform administration (ROLE_ADMIN only)")
public class AdminController {

    private final AdminManagementService adminManagementService;

    /* ─── Users ─── */

    @GetMapping("/users")
    @Operation(summary = "Search and page registered users")
    public ResponseEntity<ApiResponse<Page<UserRow>>> listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(adminManagementService.listUsers(q, role, status, page, size)));
    }

    @PutMapping("/users/{id}/activate")
    @Operation(summary = "Set user active status")
    public ResponseEntity<ApiResponse<Void>> setUserActive(@PathVariable String id, @RequestParam boolean active) {
        adminManagementService.setUserActive(id, active, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("User " + (active ? "activated" : "deactivated") + " successfully"));
    }

    /* ─── Manufacturers ─── */

    @GetMapping("/manufacturers")
    @Operation(summary = "Search and page manufacturers")
    public ResponseEntity<ApiResponse<Page<ManufacturerRow>>> listManufacturers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(adminManagementService.listManufacturers(q, filter, page, size)));
    }

    @GetMapping("/manufacturers/{id}")
    @Operation(summary = "Full manufacturer application for the review screen")
    public ResponseEntity<ApiResponse<ManufacturerDetail>> getManufacturer(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(adminManagementService.getManufacturer(id)));
    }

    @PutMapping("/manufacturers/{id}/review")
    @Operation(summary = "Approve or reject a manufacturer (reason required to reject)")
    public ResponseEntity<ApiResponse<Void>> reviewManufacturer(
            @PathVariable String id,
            @RequestParam boolean approve,
            @RequestParam(required = false) String reason) {
        adminManagementService.reviewManufacturer(id, approve, reason, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Manufacturer " + (approve ? "approved" : "rejected") + " successfully"));
    }

    @PutMapping("/manufacturers/{id}/verify")
    @Operation(summary = "Set manufacturer verification status")
    public ResponseEntity<ApiResponse<Void>> setVerified(@PathVariable String id, @RequestParam boolean verified) {
        adminManagementService.setManufacturerVerified(id, verified);
        return ResponseEntity.ok(ApiResponse.ok("Manufacturer " + (verified ? "verified" : "unverified") + " successfully"));
    }

    @PutMapping("/manufacturers/{id}/plan")
    @Operation(summary = "Assign a subscription plan to a manufacturer (omit planId to remove)")
    public ResponseEntity<ApiResponse<Void>> setPlan(@PathVariable String id, @RequestParam(required = false) String planId) {
        adminManagementService.setManufacturerPlan(id, planId);
        return ResponseEntity.ok(ApiResponse.ok("Subscription plan updated successfully"));
    }

    /* ─── RFQs ─── */

    @GetMapping("/rfqs")
    @Operation(summary = "Search and page RFQs")
    public ResponseEntity<ApiResponse<Page<RfqRow>>> listRfqs(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(adminManagementService.listRfqs(q, status, page, size)));
    }

    @PutMapping("/rfqs/{id}/status")
    @Operation(summary = "Move an RFQ to a new status")
    public ResponseEntity<ApiResponse<Void>> setRfqStatus(@PathVariable String id, @Valid @RequestBody RfqStatusRequest request) {
        adminManagementService.setRfqStatus(id, request.status());
        return ResponseEntity.ok(ApiResponse.ok("RFQ status updated successfully"));
    }

    /* ─── Pricing plans ─── */

    @GetMapping("/pricing/plans")
    @Operation(summary = "List subscription plans with usage")
    public ResponseEntity<ApiResponse<List<PlanRow>>> listPlans() {
        return ResponseEntity.ok(ApiResponse.of(adminManagementService.listPlans()));
    }

    @PostMapping("/pricing/plans")
    @Operation(summary = "Create a subscription plan")
    public ResponseEntity<ApiResponse<Void>> createPlan(@Valid @RequestBody PlanRequest request) {
        adminManagementService.createPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Plan created successfully"));
    }

    @PutMapping("/pricing/plans/{id}")
    @Operation(summary = "Update a subscription plan")
    public ResponseEntity<ApiResponse<Void>> updatePlan(@PathVariable String id, @Valid @RequestBody PlanRequest request) {
        adminManagementService.updatePlan(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Plan updated successfully"));
    }

    @DeleteMapping("/pricing/plans/{id}")
    @Operation(summary = "Delete an unused subscription plan")
    public ResponseEntity<ApiResponse<Void>> deletePlan(@PathVariable String id) {
        adminManagementService.deletePlan(id);
        return ResponseEntity.ok(ApiResponse.ok("Plan deleted successfully"));
    }
}
