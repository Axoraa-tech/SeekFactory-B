package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.user.UserResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.UserService;

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