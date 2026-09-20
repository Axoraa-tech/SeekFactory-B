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
     * Invite a new admin. Generates an invitation token and "sends" it via email.
     */
    void inviteAdmin(String email, String inviterId);

    /**
     * Completes the admin setup by validating the UUID token, checking the email, and setting the initial password.
     * Marks the token as used and drops the user into the TOTP flow by generating the secret.
     */
    AdminSetupResponse setupPassword(String token, String email, String newPassword);
}
