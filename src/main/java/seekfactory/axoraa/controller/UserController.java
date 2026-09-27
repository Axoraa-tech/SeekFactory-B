package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Request.user.BuyerPlanUpdateRequest;
import seekfactory.axoraa.dto.Request.user.UserUpdateRequest;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;
import seekfactory.axoraa.dto.Response.reel.FeedItemResponse;
import seekfactory.axoraa.dto.Response.user.UserResponse;
import seekfactory.axoraa.services.services.ManufacturerService;
import seekfactory.axoraa.services.services.ProductService;
import seekfactory.axoraa.services.services.ReelService;
import seekfactory.axoraa.services.services.UserService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.util.List;

/**
 * User profile management — AUTHENTICATED only.
 *
 * Users can view and update their own profile.
 * Partial updates are supported — only non-null fields are updated.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "User account and profile management")
public class UserController {

    private final UserService userService;
    private final ProductService productService;
    private final ReelService reelService;
    private final ManufacturerService manufacturerService;

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(userService.getCurrentUser(userId)));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile (partial update)")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @Valid @RequestBody UserUpdateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        UserResponse updated = userService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.of(updated, "Profile updated"));
    }

    @PutMapping("/me/plan")
    @Operation(summary = "Switch buyer membership plan (free, pro, enterprise) — no payment is collected yet")
    public ResponseEntity<ApiResponse<UserResponse>> updatePlan(@Valid @RequestBody BuyerPlanUpdateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(userService.updatePlan(userId, request.getPlan()), "Plan updated"));
    }

    @GetMapping("/me/saved/products")
    @Operation(summary = "Products the current user saved")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> savedProducts() {
        return ResponseEntity.ok(ApiResponse.of(productService.listSaved(SecurityUtils.getCurrentUserId())));
    }

    @GetMapping("/me/saved/seeks")
    @Operation(summary = "Seeks (video reels) the current user saved")
    public ResponseEntity<ApiResponse<List<FeedItemResponse>>> savedSeeks() {
        return ResponseEntity.ok(ApiResponse.of(reelService.listSaved(SecurityUtils.getCurrentUserId())));
    }

    @GetMapping("/me/following")
    @Operation(summary = "Manufacturers the current user follows")
    public ResponseEntity<ApiResponse<List<ManufacturerResponse>>> following() {
        return ResponseEntity.ok(ApiResponse.of(manufacturerService.listFollowing(SecurityUtils.getCurrentUserId())));
    }
}
