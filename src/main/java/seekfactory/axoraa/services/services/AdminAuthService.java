package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Request.auth.AdminAuthRequest;
import seekfactory.axoraa.dto.Response.auth.AdminSetupResponse;
import seekfactory.axoraa.dto.Response.auth.AuthResponse;

public interface AdminAuthService {
    
    /**
     * Generates a TOTP secret and QR code for an admin who hasn't set up 2FA yet.
     */
    AdminSetupResponse setupTotp(String adminEmail);

    /**
     * Verifies the email, password, and 6-digit TOTP code, then issues a JWT.
     */
    AuthResponse login(AdminAuthRequest request);
    /**
     * Invite a new admin. Generates and returns a one-time invitation token.
     * Email is not configured yet, so the inviting admin shares the setup link themselves.
     */
    String inviteAdmin(String email, String inviterId);

    /**
     * Completes the admin setup by validating the UUID token, checking the email, and setting the initial password.
     * Marks the token as used and drops the user into the TOTP flow by generating the secret.
     */
    AdminSetupResponse setupPassword(String token, String email, String newPassword);
}
