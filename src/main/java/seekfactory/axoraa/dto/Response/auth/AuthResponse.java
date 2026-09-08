package seekfactory.axoraa.dto.Response.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JWT authentication response.
 * Contains access token (short-lived) and refresh token (long-lived).
 * Also includes basic user info so frontends can immediately populate the UI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;

    // ─── User Info (avoids a separate /me call after login) ───
    private String userId;
    private String name;
    private String email;
    private String role;
    private String companyName;
    private String avatarUrl;
}