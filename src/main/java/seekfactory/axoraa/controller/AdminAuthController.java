package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Request.auth.AdminAuthRequest;
import seekfactory.axoraa.dto.Request.auth.AdminInviteRequest;
import seekfactory.axoraa.dto.Request.auth.AdminSetupPasswordRequest;
import seekfactory.axoraa.dto.Response.auth.AdminSetupResponse;
import seekfactory.axoraa.dto.Response.auth.AuthResponse;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.services.services.AdminAuthService;
import seekfactory.axoraa.utils.SecurityUtils;

@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
@Tag(name = "Admin Auth", description = "TOTP secured authentication for administrators")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;



    @PostMapping("/invite")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Invite a new Admin via Email")
    public ResponseEntity<ApiResponse<Void>> inviteAdmin(@Valid @RequestBody AdminInviteRequest request) {
        String currentAdminId = SecurityUtils.getCurrentUserId();
        adminAuthService.inviteAdmin(request.getEmail(), currentAdminId);
        return ResponseEntity.ok(ApiResponse.ok("Invitation sent successfully. Link printed in console."));
    }

    @PostMapping("/setup-password")
    @Operation(summary = "Set up an admin password from an invite token")
    public ResponseEntity<ApiResponse<AdminSetupResponse>> setupPassword(
            @Valid @RequestBody AdminSetupPasswordRequest request
    ) {
        AdminSetupResponse response = adminAuthService.setupPassword(request.getToken(), request.getEmail(), request.getPassword());
        return ResponseEntity.ok(ApiResponse.of(response, "Admin password and TOTP initialized successfully"));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with Admin credentials and 6-digit TOTP code")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AdminAuthRequest request) {
        AuthResponse authResponse = adminAuthService.login(request);
        return ResponseEntity.ok(ApiResponse.of(authResponse, "Admin login successful"));
    }
}
