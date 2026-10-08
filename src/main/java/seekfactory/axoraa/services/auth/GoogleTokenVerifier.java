package seekfactory.axoraa.services.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.UnauthorizedException;

import java.util.List;

/**
 * Verifies Google Sign-In ID tokens: Google's signature, our client ID as audience, issuer and expiry.
 * Google's public keys are fetched and cached by the verifier itself.
 */
@Slf4j
@Component
public class GoogleTokenVerifier {

    /** The identity Google vouches for once a token checks out. */
    public record GoogleIdentity(String subject, String email, boolean emailVerified, String name, String pictureUrl) {}

    private final GoogleIdTokenVerifier verifier;
    private final String clientId;

    public GoogleTokenVerifier(@Value("${app.google.client-id:}") String clientId) {
        this.clientId = clientId;
        if (clientId == null || clientId.isBlank()) {
            log.warn("app.google.client-id is not set: Google sign-in is disabled");
            this.verifier = null;
        } else {
            this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(List.of(clientId))
                    .build();
            log.info("Google sign-in enabled for client {}", clientId);
        }
    }

    public GoogleIdentity verify(String idToken) {
        if (verifier == null) {
            throw new BadRequestException("Google sign-in is not available yet. Please sign in with email.");
        }
        GoogleIdToken token;
        try {
            token = verifier.verify(idToken);
        } catch (Exception e) {
            log.warn("Google ID token could not be verified: {}", e.getMessage());
            token = null;
        }
        if (token == null) {
            log.warn("Google ID token rejected (signature, expiry or audience mismatch; expected client {})", clientId);
            throw new UnauthorizedException("Google sign-in failed. Please try again.");
        }
        GoogleIdToken.Payload payload = token.getPayload();
        return new GoogleIdentity(
                payload.getSubject(),
                payload.getEmail(),
                Boolean.TRUE.equals(payload.getEmailVerified()),
                (String) payload.get("name"),
                (String) payload.get("picture"));
    }
}
