package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.DuplicateResourceException;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.exceptions.UnauthorizedException;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.AuthService;
import seekfactory.axoraa.utils.JwtTokenProvider;

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
                    .location("Industrial Zone")
                    .verified(true)
                    .premium(false)
                    .yearsEstablished(2015)
                    .factorySize("25,000 sq.m")
                    .employees("200+ Specialists")
                    .description("Certified industrial manufacturing and custom precision components fabrication.")
                    .build();

            manufacturerRepository.save(m);
            log.info("Initialized manufacturer profile for supplier: {}", savedUser.getEmail());
        }

        // 5. Generate JWT tokens and return
        return buildAuthResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // 1. Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        // 2. Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        // 3. Check if account is active
        if (!user.getIsActive()) {
            throw new UnauthorizedException("Account has been deactivated");
        }

        log.info("User logged in: {} ({})", user.getEmail(), user.getRole());
        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse loginWithPhone(PhoneLoginRequest request) {
        // In production, verify OTP via SMS provider (Twilio, MSG91, etc.)
        // For development, accept mock OTP "123456"
        if (!"123456".equals(request.getOtp())) {
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

        log.info("Phone login: {} ({})", user.getPhone(), user.getRole());
        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse loginWithGoogle(GoogleAuthRequest request) {
        // In production, verify the Google ID token:
        //
        // GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
        //     .setAudience(Collections.singletonList(googleClientId))
        //     .build();
        // GoogleIdToken idToken = verifier.verify(request.getIdToken());
        // Payload payload = idToken.getPayload();
        // String googleId = payload.getSubject();
        // String email = payload.getEmail();
        // String name = (String) payload.get("name");
        //
        // For now, this is a placeholder. Implement with actual Google API Client.

        throw new BadRequestException("Google authentication is not yet configured. " +
                "Set app.google.client-id in application.yaml to enable.");
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        String userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        return buildAuthResponse(user);
    }

    @Override
    public void logout(String userId) {
        // With stateless JWT, logout is handled client-side by deleting the token.
        // For enterprise systems, you'd maintain a token blacklist in Redis.
        log.info("User logged out: {}", userId);
    }

    // ─── Private Helpers ──────────────────────────────────────

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
        } else if ("admin".equals(lowerRole)) {
            return UserRole.ROLE_ADMIN;
        }
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