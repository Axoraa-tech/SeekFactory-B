package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.config.JwtConfig;
import seekfactory.axoraa.dto.Request.auth.*;
import seekfactory.axoraa.dto.Response.auth.AuthResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.AuthProvider;
import seekfactory.axoraa.enums.UserRole;
import seekfactory.axoraa.enums.VerificationStatus;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.DuplicateResourceException;
import seekfactory.axoraa.exceptions.ForbiddenException;
import seekfactory.axoraa.exceptions.UnauthorizedException;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.auth.GoogleTokenVerifier;
import seekfactory.axoraa.services.auth.GoogleTokenVerifier.GoogleIdentity;
import seekfactory.axoraa.services.services.AccountService;
import seekfactory.axoraa.services.services.AuthService;
import seekfactory.axoraa.services.services.PresenceService;
import seekfactory.axoraa.utils.JwtTokenProvider;

import java.time.Instant;

/**
 * Enterprise-grade authentication service.
 *
 * Supports three login methods:
 * 1. Email + Password (LOCAL provider)
 * 2. Phone + OTP (PHONE provider) — mock OTP "123456" for development
 * 3. Google OAuth2 (GOOGLE provider) — verifies Google ID tokens
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtConfig jwtConfig;
    private final AccountService accountService;
    private final PresenceService presenceService;
    private final GoogleTokenVerifier googleTokenVerifier;

    /** Development-only phone OTP. Blank (the default) disables phone login until an SMS provider exists. */
    @Value("${app.auth.mock-otp:}")
    private String mockOtp;

    @Override
    public AuthResponse register(RegisterRequest request) {
        // 1. Check for existing email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        // 2. Map role string to enum
        UserRole role = mapRole(request.getRole());

        // 3. Build user entity
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .authProvider(AuthProvider.LOCAL)
                .companyName(request.getCompanyName())
                .phone(request.getPhone())
                .industry(request.getIndustry())
                .country(request.getCountry())
                .build();

        // 4. Save to database
        User savedUser = userRepository.save(user);
        log.info("New user registered: {} ({})", savedUser.getEmail(), savedUser.getRole());

        // 4.1 If user is a supplier/manufacturer, automatically create their manufacturer profile
        if (role == UserRole.ROLE_SUPPLIER) {
            String company = savedUser.getCompanyName() != null && !savedUser.getCompanyName().isBlank()
                    ? savedUser.getCompanyName() : savedUser.getName();
            String slug = company.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-" + savedUser.getId().substring(0, 6);

            Manufacturer m = Manufacturer.builder()
                    .user(savedUser)
                    .name(company)
                    .slug(slug)
                    .country(savedUser.getCountry() != null ? savedUser.getCountry() : "China")
                    .location("")
                    // New factories start unverified and hidden from buyers until an
                    // admin reviews them. Profile details are supplied by the factory
                    // rather than invented here, so an admin reviews real data.
                    .verified(false)
                    .verificationStatus(VerificationStatus.PENDING)
                    .premium(false)
                    .build();

            manufacturerRepository.save(m);
            log.info("Registered manufacturer {} as PENDING admin review", savedUser.getEmail());
        }

        // 4.2 Confirm the address (best effort: signup never fails on email delivery)
        accountService.sendEmailVerification(savedUser);

        // 5. Generate JWT tokens and return
        return signedIn(savedUser);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // 1. Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        // 2. Verify password
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        // 3. Check if account is active
        if (!user.getIsActive()) {
            throw new UnauthorizedException("Account has been deactivated");
        }

        // 4. Admins must pass the authenticator (TOTP) step via /api/v1/admin/auth/login
        requireNonAdmin(user);

        log.info("User logged in: {} ({})", user.getEmail(), user.getRole());
        return signedIn(user);
    }

    @Override
    public AuthResponse loginWithPhone(PhoneLoginRequest request) {
        // No SMS provider is wired yet (MSG91 / Aliyun SMS). Until then phone login only works
        // where a development code is configured (app.auth.mock-otp); elsewhere it is switched off.
        if (mockOtp == null || mockOtp.isBlank()) {
            throw new BadRequestException("Phone login is not available yet. Please sign in with email.");
        }
        if (!mockOtp.equals(request.getOtp())) {
            throw new UnauthorizedException("Invalid OTP");
        }

        // Find or create user by phone
        User user = userRepository.findByPhone(request.getPhone())
                .orElseGet(() -> {
                    // Auto-register on first phone login
                    User newUser = User.builder()
                            .phone(request.getPhone())
                            .name("User " + request.getPhone().substring(
                                    Math.max(0, request.getPhone().length() - 4)))
                            .role(mapRole(request.getRole()))
                            .authProvider(AuthProvider.PHONE)
                            .companyName(request.getCompanyName())
                            .build();
                    return userRepository.save(newUser);
                });

        requireNonAdmin(user);

        log.info("Phone login: {} ({})", user.getPhone(), user.getRole());
        return signedIn(user);
    }

    @Override
    public AuthResponse loginWithGoogle(GoogleAuthRequest request) {
        // Google sign-in is for buyers only; manufacturers use email/phone (WeChat later)
        if (mapRole(request.getRole()) != UserRole.ROLE_BUYER) {
            throw new BadRequestException("Google sign-in is only available for buyers");
        }

        GoogleIdentity google = googleTokenVerifier.verify(request.getIdToken());
        if (!google.emailVerified() || google.email() == null || google.email().isBlank()) {
            throw new UnauthorizedException("Your Google account email is not verified");
        }
        String email = google.email().toLowerCase();

        // 1. Returning Google user; 2. existing account with the same verified email gets linked;
        // 3. otherwise a new buyer account
        User user = userRepository.findByGoogleId(google.subject())
                .or(() -> userRepository.findByEmail(email).map(existing -> {
                    existing.setGoogleId(google.subject());
                    // Whoever registered this unverified address may not own it: drop their password so a
                    // pre-registered account cannot keep access once the real owner signs in with Google
                    if (!Boolean.TRUE.equals(existing.getEmailVerified())) {
                        existing.setPasswordHash(null);
                    }
                    // Google has confirmed the address, so it no longer needs our email verification
                    existing.setEmailVerified(true);
                    // Fill the photo from Google only when the account has none of its own
                    if (existing.getAvatarUrl() == null || existing.getAvatarUrl().isBlank()) {
                        existing.setAvatarUrl(google.pictureUrl());
                    }
                    log.info("Linked Google account to existing user {}", existing.getEmail());
                    return existing;
                }))
                .orElseGet(() -> {
                    String name = google.name() != null && !google.name().isBlank()
                            ? google.name() : email.substring(0, email.indexOf('@'));
                    User created = userRepository.save(User.builder()
                            .email(email)
                            .name(name)
                            .googleId(google.subject())
                            .avatarUrl(google.pictureUrl())
                            .role(UserRole.ROLE_BUYER)
                            .authProvider(AuthProvider.GOOGLE)
                            .emailVerified(true)
                            .build());
                    log.info("New buyer registered via Google: {}", created.getEmail());
                    return created;
                });

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException("Account has been deactivated");
        }
        requireNonAdmin(user);

        log.info("Google login: {} ({})", user.getEmail(), user.getRole());
        return signedIn(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (!jwtTokenProvider.isValidRefreshToken(refreshToken)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        String userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException("Account has been deactivated");
        }

        return buildAuthResponse(user);
    }

    @Override
    public void logout(String userId) {
        // With stateless JWT, logout is handled client-side by deleting the token.
        // For enterprise systems, you'd maintain a token blacklist in Redis.
        presenceService.markOffline(userId);
        log.info("User logged out: {}", userId);
    }

    // ─── Private Helpers ──────────────────────────────────────

    /** Clients detect this message to show the authenticator-code step; no tokens are issued. */
    static final String TOTP_REQUIRED = "TOTP_REQUIRED: Admin accounts must sign in with an authenticator code";

    private void requireNonAdmin(User user) {
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            throw new ForbiddenException(TOTP_REQUIRED);
        }
    }

    /** Tokens for an interactive sign-in, flagging the account's very first one. */
    private AuthResponse signedIn(User user) {
        boolean first = user.getLastLoginAt() == null;
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        AuthResponse response = buildAuthResponse(user);
        response.setFirstLogin(first);
        return response;
    }

    private AuthResponse buildAuthResponse(User user) {
        String roleStr = user.getRole().name();
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), roleStr);
        String refreshToken = jwtTokenProvider.generateRefreshToken(
                user.getId(), user.getEmail(), roleStr);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtConfig.getAccessTokenExpiry() / 1000)  // Convert ms to seconds
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(mapRoleToFrontend(user.getRole()))
                .companyName(user.getCompanyName())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    /**
     * Maps frontend role strings ("Buyer", "Supplier") to backend UserRole enum.
     */
    private UserRole mapRole(String role) {
        String lowerRole = role.toLowerCase();
        if ("buyer".equals(lowerRole)) {
            return UserRole.ROLE_BUYER;
        } else if ("supplier".equals(lowerRole) || "manufacturer".equals(lowerRole)) {
            return UserRole.ROLE_SUPPLIER;
        }
        // Admins are never created through public signup; they come from AdminAuthService invitations
        throw new BadRequestException("Invalid role: " + role + ". Must be 'Buyer' or 'Supplier'");
    }

    /**
     * Maps backend UserRole enum to frontend-friendly strings.
     */
    private String mapRoleToFrontend(UserRole role) {
        if (role == UserRole.ROLE_BUYER) {
            return "Buyer";
        } else if (role == UserRole.ROLE_SUPPLIER) {
            return "Supplier";
        } else if (role == UserRole.ROLE_ADMIN) {
            return "Admin";
        }
        return "Unknown";
    }
}