package seekfactory.axoraa.services.serviceImpl;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import dev.samstevens.totp.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Request.auth.AdminAuthRequest;
import seekfactory.axoraa.dto.Response.auth.AdminSetupResponse;
import seekfactory.axoraa.dto.Response.auth.AuthResponse;
import seekfactory.axoraa.entity.AdminInvitation;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.AuthProvider;
import seekfactory.axoraa.enums.UserRole;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.DuplicateResourceException;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.exceptions.UnauthorizedException;
import seekfactory.axoraa.repository.AdminInvitationRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.AdminAuthService;
import seekfactory.axoraa.utils.JwtTokenProvider;
import seekfactory.axoraa.config.JwtConfig;

import java.time.Instant;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminAuthServiceImpl implements AdminAuthService {

    private final UserRepository userRepository;
    private final AdminInvitationRepository adminInvitationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtConfig jwtConfig;

    private static final String ROOT_INVITE_TOKEN = "00000000-0000-0000-0000-000000000000";

    @Value("${app.admin.allow-root-reset:false}")
    private boolean allowRootReset;

    @Override
    public AdminSetupResponse setupTotp(String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin", "email", adminEmail));

        if (admin.getRole() != UserRole.ROLE_ADMIN) {
            throw new UnauthorizedException("User is not an admin");
        }

        if (admin.getIsTotpEnabled()) {
            throw new BadRequestException("TOTP is already set up for this admin");
        }

        // Generate a new secret
        SecretGenerator secretGenerator = new DefaultSecretGenerator();
        String secret = secretGenerator.generate();

        // Save secret to admin
        admin.setTotpSecret(secret);
        userRepository.save(admin);

        // Generate QR code
        QrData data = new QrData.Builder()
                .label(admin.getEmail())
                .secret(secret)
                .issuer("SeekFactory Admin")
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        QrGenerator generator = new ZxingPngQrGenerator();
        byte[] imageData;
        try {
            imageData = generator.generate(data);
        } catch (QrGenerationException e) {
            log.error("Failed to generate QR code", e);
            throw new RuntimeException("Failed to generate QR code", e);
        }

        String mimeType = generator.getImageMimeType();
        String dataUri = Utils.getDataUriForImage(imageData, mimeType);

        return AdminSetupResponse.builder()
                .secret(secret)
                .qrCodeUri(dataUri)
                .build();
    }

    @Override
    public AuthResponse login(AdminAuthRequest request) {
        // 1. Verify credentials
        User admin = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (admin.getRole() != UserRole.ROLE_ADMIN) {
            throw new UnauthorizedException("Access denied. Admin role required.");
        }

        if (!admin.getIsActive()) {
            throw new UnauthorizedException("Admin account is deactivated");
        }

        if (admin.getTotpSecret() == null) {
            throw new UnauthorizedException("TOTP not configured for this admin account");
        }

        // 2. Verify TOTP Code
        TimeProvider timeProvider = new SystemTimeProvider();
        DefaultCodeGenerator codeGenerator = new DefaultCodeGenerator();
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        verifier.setAllowedTimePeriodDiscrepancy(3); // Allow +/- 90 seconds clock skew
        
        // Allows a window of 30 seconds before or after the current time
        boolean isCodeValid = verifier.isValidCode(admin.getTotpSecret(), request.getCode());
        
        if (!isCodeValid) {
            // Never log the TOTP secret or valid codes: anyone with log access could log in as this admin
            log.warn("Invalid TOTP attempt for admin {} at {}", admin.getEmail(), Instant.now());
            throw new UnauthorizedException("Invalid TOTP code");
        }

        // If this was their first time logging in, mark TOTP as enabled
        if (!admin.getIsTotpEnabled()) {
            admin.setIsTotpEnabled(true);
            userRepository.save(admin);
            log.info("Admin {} successfully completed TOTP setup", admin.getEmail());
        }

        // 3. Issue Token
        log.info("Admin {} successfully logged in via TOTP", admin.getEmail());
        
        String accessToken = jwtTokenProvider.generateAccessToken(admin.getId().toString(), admin.getEmail(), admin.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(admin.getId().toString(), admin.getEmail(), admin.getRole().name());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtConfig.getAccessTokenExpiry())
                .build();
    }

    @Override
    public String inviteAdmin(String email, String inviterId) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("User", "email", email);
        }
        
        if (adminInvitationRepository.existsByEmailAndIsUsedFalse(email)) {
            throw new BadRequestException("An active invitation already exists for this email.");
        }

        User inviter = null;
        if (inviterId != null) {
            inviter = userRepository.findById(inviterId).orElse(null);
        }

        AdminInvitation invitation = AdminInvitation.builder()
                .email(email)
                .expiresAt(LocalDateTime.now().plusDays(7)) // Links expire in 7 days
                .invitedBy(inviter)
                .build();
                
        adminInvitationRepository.save(invitation);
        
        // The token is returned to the inviting admin only; it is not logged, since anyone
        // with log access could otherwise use it to take the invitation
        log.info("Admin invitation created for {} by {}", email, inviterId);
        return invitation.getToken();
    }

    @Override
    public AdminSetupResponse setupPassword(String token, String email, String newPassword) {
        AdminInvitation invitation = adminInvitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired setup token."));

        // The fixed root token is public (it is in the V4 migration), so its bypasses are only
        // honoured when explicitly enabled for a one-off local recovery via app.admin.allow-root-reset
        boolean isRootToken = allowRootReset && ROOT_INVITE_TOKEN.equals(token);

        if (invitation.getIsUsed() && !isRootToken) {
            throw new IllegalArgumentException("This invitation has already been used.");
        }
        
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now()) && !isRootToken) {
            throw new IllegalArgumentException("This invitation has expired.");
        }

        if (!invitation.getEmail().equalsIgnoreCase(email) && !isRootToken) {
            throw new IllegalArgumentException("The provided email does not match the invitation.");
        }

        // 2. Check if user already exists
        User adminUser = userRepository.findByEmail(email).orElse(null);
        
        if (adminUser != null) {
            if (!isRootToken || adminUser.getRole() != UserRole.ROLE_ADMIN) {
                throw new DuplicateResourceException("User", "email", email);
            }
            // Reset existing user's password and TOTP for dev testing
            adminUser.setPasswordHash(passwordEncoder.encode(newPassword));
            adminUser.setIsTotpEnabled(false);
            userRepository.save(adminUser);
        } else {
            // 1. Create the user
            adminUser = User.builder()
                    .email(email)
                    .name("Admin") // Can be updated by admin later
                    .passwordHash(passwordEncoder.encode(newPassword))
                    .role(UserRole.ROLE_ADMIN)
                    .authProvider(AuthProvider.LOCAL)
                    .country("Unknown")
                    .build();
            userRepository.save(adminUser);
        }

        userRepository.save(adminUser);

        // 2. Mark token as used
        invitation.setIsUsed(true);
        adminInvitationRepository.save(invitation);

        log.info("Admin {} successfully created via invitation link", invitation.getEmail());

        // 3. Kick off TOTP setup
        return setupTotp(adminUser.getEmail());
    }
}
