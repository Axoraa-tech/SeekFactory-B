package seekfactory.axoraa.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import seekfactory.axoraa.config.JwtConfig;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Centralized JWT utility for token generation, validation, and claim extraction.
 *
 * Generates two types of tokens:
 * - Access Token (short-lived, 24h default) — sent with every API request
 * - Refresh Token (long-lived, 7d default) — used to get new access tokens
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String TYPE_CLAIM = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final String TYPE_UPLOAD = "upload";
    /** Upload passes only need to outlive one file transfer. */
    public static final long UPLOAD_TOKEN_EXPIRY_MS = 5 * 60 * 1000;

    private final JwtConfig jwtConfig;

    /**
     * Generate an access token for the given user.
     */
    public String generateAccessToken(String userId, String email, String role) {
        return buildToken(userId, email, role, TYPE_ACCESS, jwtConfig.getAccessTokenExpiry());
    }

    /**
     * Generate a refresh token for the given user.
     */
    public String generateRefreshToken(String userId, String email, String role) {
        return buildToken(userId, email, role, TYPE_REFRESH, jwtConfig.getRefreshTokenExpiry());
    }

    /**
     * Short-lived pass that only authorises a direct media upload. Browsers send files straight to
     * this API with it, because the web app's own proxy (Vercel) rejects request bodies over 4.5 MB.
     */
    public String generateUploadToken(String userId, String role) {
        return buildToken(userId, null, role, TYPE_UPLOAD, UPLOAD_TOKEN_EXPIRY_MS);
    }

    /** True for a valid, unexpired upload pass. Only accepted on the upload endpoints. */
    public boolean isValidUploadToken(String token) {
        return validateToken(token) && TYPE_UPLOAD.equals(getClaims(token).get(TYPE_CLAIM, String.class));
    }

    /**
     * Validate a token and return true if it is valid and not expired.
     */
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            System.err.println("JWT Validation Failed: " + e.getMessage());
            return false;
        }
    }

    /** True for a valid, unexpired access token. Refresh tokens are rejected for API calls. */
    public boolean isValidAccessToken(String token) {
        return validateToken(token) && TYPE_ACCESS.equals(getClaims(token).get(TYPE_CLAIM, String.class));
    }

    /** True for a valid, unexpired refresh token. Access tokens cannot be used to refresh. */
    public boolean isValidRefreshToken(String token) {
        return validateToken(token) && TYPE_REFRESH.equals(getClaims(token).get(TYPE_CLAIM, String.class));
    }

    /**
     * Extract the user ID (subject) from a valid token.
     */
    public String getUserIdFromToken(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * Extract the user's email from a valid token.
     */
    public String getEmailFromToken(String token) {
        return getClaims(token).get("email", String.class);
    }

    /**
     * Extract the user's role from a valid token.
     */
    public String getRoleFromToken(String token) {
        return getClaims(token).get("role", String.class);
    }

    // ─── Private Helpers ──────────────────────────────────────

    private String buildToken(String userId, String email, String role, String type, long expiry) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiry);

        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("role", role)
                .claim(TYPE_CLAIM, type)
                .issuer(jwtConfig.getIssuer())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtConfig.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}