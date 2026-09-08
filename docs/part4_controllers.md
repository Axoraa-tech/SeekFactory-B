# SeekFactory Backend — Part 4: Controllers + Missing DTO

> All 11 REST Controller classes + ManufacturerUpdateRequest DTO.

---

## 0. Missing DTO — `ManufacturerUpdateRequest.java`

```java
package seekfactory.axoraa.dto.request.manufacturer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManufacturerUpdateRequest {

    private String name;
    private String logoUrl;
    private String coverUrl;
    private String location;
    private String factorySize;
    private String employees;
    private String description;
    private String chairmanName;
    private List<String> exportCountries;
    private List<String> categoryIds;
}
```

---

## 1. `AuthController.java`

```java
package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.request.auth.*;
import seekfactory.axoraa.dto.response.auth.AuthResponse;
import seekfactory.axoraa.dto.response.common.ApiResponse;
import seekfactory.axoraa.dto.response.user.UserResponse;
import seekfactory.axoraa.services.AuthService;
import seekfactory.axoraa.services.UserService;
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
```

---

## 2. `FeedController.java`

```java
package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.response.common.ApiResponse;
import seekfactory.axoraa.dto.response.reel.FeedItemResponse;
import seekfactory.axoraa.enums.FeedTab;
import seekfactory.axoraa.services.ReelService;

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
```

---

## 3. `CategoryController.java`

```java
package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.response.category.CategoryResponse;
import seekfactory.axoraa.dto.response.common.ApiResponse;
import seekfactory.axoraa.services.CategoryService;

import java.util.List;

/**
 * Machinery taxonomy categories — ALL PUBLIC.
 *
 * Serves the hierarchical category tree:
 * - /categories         → all categories (flat list)
 * - /categories/roots   → only root-level categories (20 sectors)
 * - /categories/{slug}/children → subcategories under a parent
 * - /categories/{slug}  → single category by slug
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Machinery taxonomy and category tree")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "List all categories (flat)")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.of(categoryService.listAll()));
    }

    @GetMapping("/roots")
    @Operation(summary = "List root categories only")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> listRoots() {
        return ResponseEntity.ok(ApiResponse.of(categoryService.listRoots()));
    }

    @GetMapping("/{slug}/children")
    @Operation(summary = "List subcategories under a parent category")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> listChildren(
            @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.of(categoryService.listChildren(slug)));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get a single category by slug")
    public ResponseEntity<ApiResponse<CategoryResponse>> getBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.of(categoryService.getBySlug(slug)));
    }
}
```

---

## 4. `ManufacturerController.java`

```java
package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.response.common.ApiResponse;
import seekfactory.axoraa.dto.response.manufacturer.ManufacturerDetailResponse;
import seekfactory.axoraa.dto.response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.services.ManufacturerService;

import java.util.List;

/**
 * Manufacturer / Factory profiles — ALL PUBLIC.
 *
 * The detail endpoint (/manufacturers/{slug}) returns a composite response
 * with the factory profile, its products, and its video reels.
 */
@RestController
@RequestMapping("/api/v1/manufacturers")
@RequiredArgsConstructor
@Tag(name = "Manufacturers", description = "Factory profiles and verified suppliers")
public class ManufacturerController {

    private final ManufacturerService manufacturerService;

    @GetMapping
    @Operation(summary = "List all manufacturers")
    public ResponseEntity<ApiResponse<List<ManufacturerResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.of(manufacturerService.listAll()));
    }

    @GetMapping("/verified")
    @Operation(summary = "List verified manufacturers (sorted by follower count)")
    public ResponseEntity<ApiResponse<List<ManufacturerResponse>>> listVerified(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.of(manufacturerService.listVerified(limit)));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get manufacturer detail by slug (includes products and reels)")
    public ResponseEntity<ApiResponse<ManufacturerDetailResponse>> getBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.of(manufacturerService.getBySlug(slug)));
    }
}
```

---

## 5. `ProductController.java`

```java
package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.response.common.ApiResponse;
import seekfactory.axoraa.dto.response.product.ProductDetailResponse;
import seekfactory.axoraa.dto.response.product.ProductResponse;
import seekfactory.axoraa.services.ProductService;

import java.util.List;

/**
 * Product catalog — ALL PUBLIC.
 *
 * The detail endpoint (/products/{slug}) returns:
 * - Full product info with specs (JSONB)
 * - Manufacturer info (for factory badge)
 * - Related products from the same manufacturer
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog and specifications")
public class ProductController {

    private final ProductService productService;

    @GetMapping("/trending")
    @Operation(summary = "List trending products")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> listTrending(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.of(productService.listTrending(limit)));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get product detail by slug (includes manufacturer and related products)")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.of(productService.getBySlug(slug)));
    }

    @GetMapping("/category/{categoryId}")
    @Operation(summary = "List products by category")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> listByCategory(
            @PathVariable String categoryId) {
        return ResponseEntity.ok(ApiResponse.of(productService.listByCategory(categoryId)));
    }
}
```

---

## 6. `CommentController.java`

```java
package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.request.comment.CommentCreateRequest;
import seekfactory.axoraa.dto.request.comment.ReplyCreateRequest;
import seekfactory.axoraa.dto.response.comment.CommentReplyResponse;
import seekfactory.axoraa.dto.response.comment.CommentResponse;
import seekfactory.axoraa.dto.response.common.ApiResponse;
import seekfactory.axoraa.services.CommentService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.util.List;

/**
 * Reel comments and replies.
 *
 * Reading comments is PUBLIC (anyone can view).
 * Writing comments/replies requires AUTHENTICATION.
 *
 * URL pattern follows REST conventions:
 * - GET  /api/v1/reels/{reelId}/comments      → list comments (public)
 * - POST /api/v1/reels/{reelId}/comments      → add comment (auth)
 * - POST /api/v1/comments/{commentId}/replies  → add reply (auth)
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Reel comments and threaded replies")
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/api/v1/reels/{reelId}/comments")
    @Operation(summary = "List comments for a reel (public)")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> listByReel(
            @PathVariable String reelId) {
        return ResponseEntity.ok(ApiResponse.of(commentService.listByReelId(reelId)));
    }

    @PostMapping("/api/v1/reels/{reelId}/comments")
    @Operation(summary = "Add a comment to a reel (requires authentication)")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @PathVariable String reelId,
            @Valid @RequestBody CommentCreateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        CommentResponse comment = commentService.addComment(reelId, userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of(comment, "Comment added"));
    }

    @PostMapping("/api/v1/comments/{commentId}/replies")
    @Operation(summary = "Reply to a comment (requires authentication)")
    public ResponseEntity<ApiResponse<CommentReplyResponse>> addReply(
            @PathVariable String commentId,
            @Valid @RequestBody ReplyCreateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        CommentReplyResponse reply = commentService.addReply(commentId, userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of(reply, "Reply added"));
    }
}
```

---

## 7. `RfqController.java`

```java
package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.request.rfq.RfqCreateRequest;
import seekfactory.axoraa.dto.response.common.ApiResponse;
import seekfactory.axoraa.dto.response.rfq.RfqResponse;
import seekfactory.axoraa.services.RfqService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.util.List;
import java.util.Map;

/**
 * Request for Quotation management — AUTHENTICATED only.
 *
 * Buyers submit RFQs and can view their own RFQ history.
 * The submit endpoint returns { ok: true, id: "..." } to match
 * the frontend RfqRepository.submit() contract.
 */
@RestController
@RequestMapping("/api/v1/rfqs")
@RequiredArgsConstructor
@Tag(name = "RFQ", description = "Request for Quotation management")
public class RfqController {

    private final RfqService rfqService;

    @PostMapping
    @Operation(summary = "Submit a new RFQ")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submit(
            @Valid @RequestBody RfqCreateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        RfqResponse rfq = rfqService.submit(userId, request);

        // Return format matching frontend contract: { ok: true, id: "..." }
        Map<String, Object> result = Map.of(
                "ok", true,
                "id", rfq.getId(),
                "referenceNumber", rfq.getReferenceNumber()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of(result, "RFQ submitted successfully"));
    }

    @GetMapping
    @Operation(summary = "List current user's RFQs")
    public ResponseEntity<ApiResponse<List<RfqResponse>>> listMyRfqs() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(rfqService.listByUser(userId)));
    }
}
```

---

## 8. `ConversationController.java`

```java
package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.response.common.ApiResponse;
import seekfactory.axoraa.dto.response.message.ConversationResponse;
import seekfactory.axoraa.services.ConversationService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.util.List;

/**
 * B2B messaging / conversations — AUTHENTICATED only.
 *
 * Returns conversations enriched with manufacturer info,
 * matching the frontend MessageRepository.listRecent() contract.
 */
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@Tag(name = "Messages", description = "B2B buyer-supplier messaging")
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    @Operation(summary = "List recent conversations with manufacturers")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> listRecent(
            @RequestParam(defaultValue = "20") int limit) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(
                conversationService.listRecent(userId, limit)));
    }
}
```

---

## 9. `NotificationController.java`

```java
package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.response.common.ApiResponse;
import seekfactory.axoraa.dto.response.notification.NotificationResponse;
import seekfactory.axoraa.services.NotificationService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.util.List;
import java.util.Map;

/**
 * User notifications — AUTHENTICATED only.
 *
 * Supports:
 * - Listing all notifications (sorted by most recent)
 * - Getting unread count (for badge rendering)
 * - Marking all as read
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User activity notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List all notifications for current user")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> list() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(notificationService.listByUser(userId)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount() {
        String userId = SecurityUtils.getCurrentUserId();
        long count = notificationService.unreadCount(userId);
        return ResponseEntity.ok(ApiResponse.of(Map.of("count", count)));
    }

    @PutMapping("/mark-read")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead() {
        String userId = SecurityUtils.getCurrentUserId();
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.ok("All notifications marked as read"));
    }
}
```

---

## 10. `UserController.java`

```java
package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.request.user.UserUpdateRequest;
import seekfactory.axoraa.dto.response.common.ApiResponse;
import seekfactory.axoraa.dto.response.user.UserResponse;
import seekfactory.axoraa.services.UserService;
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
```

---

## 11. `AdminController.java`

```java
package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.response.common.ApiResponse;
import seekfactory.axoraa.dto.response.user.UserResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.UserService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin-only endpoints — requires ROLE_ADMIN authority.
 *
 * Provides platform administration capabilities:
 * - View all registered users
 * - Activate/deactivate user accounts
 * - Verify/unverify manufacturer profiles
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

    private final UserRepository userRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final UserService userService;

    @GetMapping("/users")
    @Operation(summary = "List all registered users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> listAllUsers() {
        List<UserResponse> users = userRepository.findAll().stream()
                .map(user -> UserResponse.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .role(user.getRole().name().replace("ROLE_", ""))
                        .avatarUrl(user.getAvatarUrl())
                        .companyName(user.getCompanyName())
                        .industry(user.getIndustry())
                        .country(user.getCountry())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.of(users));
    }

    @PutMapping("/users/{id}/activate")
    @Operation(summary = "Toggle user active status")
    public ResponseEntity<ApiResponse<Void>> toggleUserActive(
            @PathVariable String id,
            @RequestParam boolean active) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        user.setIsActive(active);
        userRepository.save(user);

        String action = active ? "activated" : "deactivated";
        return ResponseEntity.ok(ApiResponse.ok("User " + action + " successfully"));
    }

    @PutMapping("/manufacturers/{id}/verify")
    @Operation(summary = "Toggle manufacturer verification status")
    public ResponseEntity<ApiResponse<Void>> toggleVerification(
            @PathVariable String id,
            @RequestParam boolean verified) {
        Manufacturer manufacturer = manufacturerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Manufacturer", "id", id));

        manufacturer.setVerified(verified);
        manufacturerRepository.save(manufacturer);

        String action = verified ? "verified" : "unverified";
        return ResponseEntity.ok(ApiResponse.ok("Manufacturer " + action + " successfully"));
    }
}
```

---

> **All 11 controllers are now complete.** Combined with Parts 1–3, you now have 100% of the backend code needed to build the SeekFactory REST API.
