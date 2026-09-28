package seekfactory.axoraa.services.services;

import seekfactory.axoraa.entity.User;

/** Password management and email verification. */
public interface AccountService {

    void changePassword(String userId, String currentPassword, String newPassword);

    /** Emails a reset link when the address belongs to an account; silent otherwise. */
    void requestPasswordReset(String email);

    void resetPassword(String token, String newPassword);

    /** Emails a verification link to the user's address. */
    void sendEmailVerification(String userId);

    /** Best-effort verification email for a just-created account (never throws for cooldown). */
    void sendEmailVerification(User user);

    void verifyEmail(String token);
}
