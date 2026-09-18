package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Request.user.UserUpdateRequest;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.user.UserResponse;
import seekfactory.axoraa.services.services.UserService;
import seekfactory.axoraa.utils.SecurityUtils;

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
}