package seekfactory.axoraa.services.serviceImpl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.entity.AccountToken;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.AccountTokenPurpose;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.AccountTokenRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.AccountService;
import seekfactory.axoraa.services.services.MailService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@Transactional
public class AccountServiceImpl implements AccountService {

    static final Duration RESET_TTL = Duration.ofMinutes(30);
    static final Duration VERIFY_TTL = Duration.ofHours(24);
    /** At most one email per purpose per user in this window, so the endpoints can't be used to spam. */
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AccountTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final String frontendUrl;

    public AccountServiceImpl(UserRepository userRepository,
                              AccountTokenRepository tokenRepository,
                              PasswordEncoder passwordEncoder,
                              MailService mailService,
                              @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Override
    public void changePassword(String userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (user.getPasswordHash() == null) {
            throw new BadRequestException("This account has no password yet. Use Forgot password to set one.");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        // An outstanding reset link is void once the password changes
        revokeOpenTokens(user.getId(), AccountTokenPurpose.PASSWORD_RESET);
        log.info("Password changed for user {}", user.getId());
    }

    @Override
    public void requestPasswordReset(String email) {
        // Same outcome whether or not the account exists (no account enumeration)
        userRepository.findByEmail(email.trim())
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .ifPresent(user -> {
                    String token = issue(user, AccountTokenPurpose.PASSWORD_RESET, RESET_TTL);
                    if (token == null) return;
                    mailService.send(user.getEmail(), "Reset your SeekFactory password",
                            "Hi " + user.getName() + ",\n\n"
                                    + "We received a request to reset your SeekFactory password. "
                                    + "Open this link within 30 minutes to choose a new one:\n\n"
                                    + frontendUrl + "/reset-password?token=" + token + "\n\n"
                                    + "If you did not ask for this, ignore this email and your password stays the same.");
                });
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        AccountToken accountToken = consume(token, AccountTokenPurpose.PASSWORD_RESET,
                "This reset link is invalid or has expired. Request a new one.");
        User user = accountToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // Receiving the link proves the user controls the address
        user.setEmailVerified(true);
        userRepository.save(user);
        revokeOpenTokens(user.getId(), AccountTokenPurpose.PASSWORD_RESET);
        log.info("Password reset for user {}", user.getId());
    }

    @Override
    public void sendEmailVerification(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BadRequestException("Add an email address to your account first");
        }
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new BadRequestException("Your email is already verified");
        }
        if (!sendVerificationMail(user)) {
            throw new BadRequestException("A verification email was just sent. Please wait a minute before retrying.");
        }
    }

    @Override
    public void sendEmailVerification(User user) {
        if (user.getEmail() != null && !user.getEmail().isBlank() && !Boolean.TRUE.equals(user.getEmailVerified())) {
            sendVerificationMail(user);
        }
    }

    private boolean sendVerificationMail(User user) {
        String token = issue(user, AccountTokenPurpose.EMAIL_VERIFICATION, VERIFY_TTL);
        if (token == null) return false;
        mailService.send(user.getEmail(), "Verify your SeekFactory email",
                "Hi " + user.getName() + ",\n\n"
                        + "Please confirm this is your email address by opening the link below (valid for 24 hours):\n\n"
                        + frontendUrl + "/verify-email?token=" + token + "\n\n"
                        + "If you did not create a SeekFactory account, you can ignore this email.");
        return true;
    }

    @Override
    public void verifyEmail(String token) {
        AccountToken accountToken = consume(token, AccountTokenPurpose.EMAIL_VERIFICATION,
                "This verification link is invalid or has expired. Request a new one from your account settings.");
        User user = accountToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        revokeOpenTokens(user.getId(), AccountTokenPurpose.EMAIL_VERIFICATION);
    }

    // ─── Tokens ────────────────────────────────────────────────

    /**
     * Creates a token (voiding older open ones of the same purpose) and returns the raw value
     * for the email link, or null when one was already issued within the cooldown.
     */
    private String issue(User user, AccountTokenPurpose purpose, Duration ttl) {
        Instant now = Instant.now();
        List<AccountToken> open = tokenRepository.findByUserIdAndPurposeAndUsedAtIsNull(user.getId(), purpose);
        boolean recent = open.stream()
                .anyMatch(t -> t.getCreatedAt() != null && t.getCreatedAt().isAfter(now.minus(RESEND_COOLDOWN)));
        if (recent) {
            log.info("Skipped {} email for user {}: cooldown", purpose, user.getId());
            return null;
        }
        open.forEach(t -> t.setUsedAt(now));
        tokenRepository.saveAll(open);

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokenRepository.save(AccountToken.builder()
                .user(user)
                .purpose(purpose)
                .tokenHash(hash(raw))
                .expiresAt(now.plus(ttl))
                .build());
        return raw;
    }

    private AccountToken consume(String rawToken, AccountTokenPurpose purpose, String invalidMessage) {
        AccountToken token = tokenRepository.findByTokenHashAndPurpose(hash(rawToken.trim()), purpose)
                .filter(t -> t.isUsable(Instant.now()))
                .filter(t -> Boolean.TRUE.equals(t.getUser().getIsActive()))
                .orElseThrow(() -> new BadRequestException(invalidMessage));
        token.setUsedAt(Instant.now());
        tokenRepository.save(token);
        return token;
    }

    private void revokeOpenTokens(String userId, AccountTokenPurpose purpose) {
        Instant now = Instant.now();
        List<AccountToken> open = tokenRepository.findByUserIdAndPurposeAndUsedAtIsNull(userId, purpose);
        open.forEach(t -> t.setUsedAt(now));
        tokenRepository.saveAll(open);
    }

    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
