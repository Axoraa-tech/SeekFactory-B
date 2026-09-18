package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Request.auth.*;
import seekfactory.axoraa.dto.Response.auth.AuthResponse;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.user.UserResponse;
import seekfactory.axoraa.services.services.AuthService;
import seekfactory.axoraa.services.services.UserService;
import seekfactory.axoraa.utils.SecurityUtils;

/**
 * Authentication endpoints — ALL PUBLIC (except /me and /logout).
 *
 * Handles:
 * - Email + password registration and login
 * - Phone + OTP login (mock OTP: "123456")
 * - Google OAuth2 login
 * - JWT token refresh
 * - Get current authenticated user
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration, login, and token management")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user with email and password")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse authResponse = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of(authResponse, "Registration successful"));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(ApiResponse.of(authResponse, "Login successful"));
    }

    @PostMapping("/login/phone")
    @Operation(summary = "Login with phone number and OTP")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithPhone(
            @Valid @RequestBody PhoneLoginRequest request) {
        AuthResponse authResponse = authService.loginWithPhone(request);
        return ResponseEntity.ok(ApiResponse.of(authResponse, "Phone login successful"));
    }

    @PostMapping("/google")
    @Operation(summary = "Login with Google OAuth2 ID token")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithGoogle(
            @Valid @RequestBody GoogleAuthRequest request) {
        AuthResponse authResponse = authService.loginWithGoogle(request);
        return ResponseEntity.ok(ApiResponse.of(authResponse, "Google login successful"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse authResponse = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.of(authResponse, "Token refreshed"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        String userId = SecurityUtils.getCurrentUserId();
        UserResponse user = userService.getCurrentUser(userId);
        return ResponseEntity.ok(ApiResponse.of(user));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout current user")
    public ResponseEntity<ApiResponse<Void>> logout() {
        String userId = SecurityUtils.getCurrentUserId();
        authService.logout(userId);
        return ResponseEntity.ok(ApiResponse.ok("Logout successful"));
    }
}