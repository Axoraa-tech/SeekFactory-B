package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Request.auth.ChangePasswordRequest;
import seekfactory.axoraa.dto.Request.auth.ForgotPasswordRequest;
import seekfactory.axoraa.dto.Request.auth.ResetPasswordRequest;
import seekfactory.axoraa.dto.Request.auth.TokenRequest;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.services.services.AccountService;
import seekfactory.axoraa.utils.SecurityUtils;

/**
 * Account security for buyers and suppliers.
 *
 * /api/v1/account/** requires a signed-in user; the emailed-link endpoints live under
 * /api/v1/auth/** (public) because the user may be signed out when they open the link.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Account", description = "Password change/reset and email verification")
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/api/v1/account/password")
    @Operation(summary = "Change password (requires the current password)")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(SecurityUtils.getCurrentUserId(),
                request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok("Password updated"));
    }

    @PostMapping("/api/v1/account/email/verification")
    @Operation(summary = "Email a verification link to the signed-in user")
    public ResponseEntity<ApiResponse<Void>> sendVerification() {
        accountService.sendEmailVerification(SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Verification email sent"));
    }

    @PostMapping("/api/v1/auth/password/forgot")
    @Operation(summary = "Email a password reset link (same response whether or not the account exists)")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        accountService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("If an account exists for that email, a reset link is on its way"));
    }

    @PostMapping("/api/v1/auth/password/reset")
    @Operation(summary = "Set a new password using an emailed reset token")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accountService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok("Password reset. You can now sign in."));
    }

    @PostMapping("/api/v1/auth/email/verify")
    @Operation(summary = "Confirm an email address using an emailed verification token")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody TokenRequest request) {
        accountService.verifyEmail(request.getToken());
        return ResponseEntity.ok(ApiResponse.ok("Email verified"));
    }
}
