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

    private final JwtConfig jwtConfig;

    /**
     * Generate an access token for the given user.
     */
    public String generateAccessToken(String userId, String email, String role) {
        return buildToken(userId, email, role, jwtConfig.getAccessTokenExpiry());
    }

    /**
     * Generate a refresh token for the given user.
     */
    public String generateRefreshToken(String userId, String email, String role) {
        return buildToken(userId, email, role, jwtConfig.getRefreshTokenExpiry());
    }

    /**
     * Validate a token and return true if it is valid and not expired.
     */
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
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

    private String buildToken(String userId, String email, String role, long expiry) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiry);

        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("role", role)
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