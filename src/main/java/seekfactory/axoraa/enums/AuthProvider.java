package seekfactory.axoraa.enums;


/**
 * Tracks how a user originally registered / authenticated.
 * Determines which credentials are required at login.
 */


public enum AuthProvider {
    LOCAL,     // Email + Password
    GOOGLE,    // Google OAuth2 ID token
    PHONE      // SMS OTP (future implementation)
}
