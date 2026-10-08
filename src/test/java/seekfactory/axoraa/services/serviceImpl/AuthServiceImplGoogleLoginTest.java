package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import seekfactory.axoraa.config.JwtConfig;
import seekfactory.axoraa.dto.Request.auth.GoogleAuthRequest;
import seekfactory.axoraa.dto.Request.auth.LoginRequest;
import seekfactory.axoraa.dto.Response.auth.AuthResponse;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.AuthProvider;
import seekfactory.axoraa.enums.UserRole;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ForbiddenException;
import seekfactory.axoraa.exceptions.UnauthorizedException;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.auth.GoogleTokenVerifier;
import seekfactory.axoraa.services.auth.GoogleTokenVerifier.GoogleIdentity;
import seekfactory.axoraa.services.services.AccountService;
import seekfactory.axoraa.utils.JwtTokenProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Google sign-in: find by Google ID, link by verified email, or create a buyer. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplGoogleLoginTest {

    @Mock private UserRepository userRepository;
    @Mock private ManufacturerRepository manufacturerRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private JwtConfig jwtConfig;
    @Mock private AccountService accountService;
    @Mock private seekfactory.axoraa.services.services.PresenceService presenceService;
    @Mock private GoogleTokenVerifier googleTokenVerifier;

    @InjectMocks private AuthServiceImpl service;

    private static final GoogleIdentity VERIFIED =
            new GoogleIdentity("g-123", "Buyer@Example.com", true, "Asha Buyer", "https://pic");

    @BeforeEach
    void setUp() {
        when(jwtTokenProvider.generateAccessToken(anyString(), anyString(), anyString())).thenReturn("access");
        when(jwtTokenProvider.generateRefreshToken(anyString(), anyString(), anyString())).thenReturn("refresh");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId("u-new");
            return u;
        });
    }

    private GoogleAuthRequest request(String role) {
        return new GoogleAuthRequest("id-token", role);
    }

    private User user(UserRole role) {
        User u = User.builder().email("buyer@example.com").name("Existing").passwordHash("hash")
                .role(role).authProvider(AuthProvider.LOCAL).isActive(true).build();
        u.setId("u-1");
        return u;
    }

    @Test
    void returningGoogleUserSignsIn() {
        User existing = user(UserRole.ROLE_BUYER);
        existing.setGoogleId("g-123");
        when(googleTokenVerifier.verify("id-token")).thenReturn(VERIFIED);
        when(userRepository.findByGoogleId("g-123")).thenReturn(Optional.of(existing));

        AuthResponse response = service.loginWithGoogle(request("Buyer"));

        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getUserId()).isEqualTo("u-1");
    }

    @Test
    void existingEmailAccountIsLinked() {
        User existing = user(UserRole.ROLE_BUYER);
        when(googleTokenVerifier.verify("id-token")).thenReturn(VERIFIED);
        when(userRepository.findByGoogleId("g-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(existing));

        service.loginWithGoogle(request("Buyer"));

        assertThat(existing.getGoogleId()).isEqualTo("g-123");
        assertThat(existing.getEmailVerified()).isTrue();
        // Linking keeps the original provider and password
        assertThat(existing.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(existing.getPasswordHash()).isEqualTo("hash");
    }

    @Test
    void linkingFillsMissingPhotoButKeepsOwnPhoto() {
        User noPhoto = user(UserRole.ROLE_BUYER);
        when(googleTokenVerifier.verify("id-token")).thenReturn(VERIFIED);
        when(userRepository.findByGoogleId("g-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(noPhoto));
        service.loginWithGoogle(request("Buyer"));
        assertThat(noPhoto.getAvatarUrl()).isEqualTo("https://pic");

        User withPhoto = user(UserRole.ROLE_BUYER);
        withPhoto.setAvatarUrl("/api/v1/media/own.jpg");
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(withPhoto));
        service.loginWithGoogle(request("Buyer"));
        assertThat(withPhoto.getAvatarUrl()).isEqualTo("/api/v1/media/own.jpg");
    }

    @Test
    void newGoogleUserBecomesVerifiedBuyerWithoutPassword() {
        when(googleTokenVerifier.verify("id-token")).thenReturn(VERIFIED);
        when(userRepository.findByGoogleId("g-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.empty());

        AuthResponse response = service.loginWithGoogle(request("Buyer"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        User created = saved.getAllValues().get(0);
        assertThat(created.getEmail()).isEqualTo("buyer@example.com");
        assertThat(created.getName()).isEqualTo("Asha Buyer");
        assertThat(created.getRole()).isEqualTo(UserRole.ROLE_BUYER);
        assertThat(created.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(created.getEmailVerified()).isTrue();
        assertThat(created.getPasswordHash()).isNull();
        assertThat(response.isFirstLogin()).isTrue();
    }

    @Test
    void unverifiedGoogleEmailIsRejected() {
        when(googleTokenVerifier.verify("id-token"))
                .thenReturn(new GoogleIdentity("g-123", "buyer@example.com", false, "X", null));

        assertThatThrownBy(() -> service.loginWithGoogle(request("Buyer")))
                .isInstanceOf(UnauthorizedException.class);
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void supplierRoleIsRejectedBeforeVerifyingToken() {
        assertThatThrownBy(() -> service.loginWithGoogle(request("Supplier")))
                .isInstanceOf(BadRequestException.class);
        verify(googleTokenVerifier, never()).verify(anyString());
    }

    @Test
    void adminAccountRequiresTotp() {
        User admin = user(UserRole.ROLE_ADMIN);
        when(googleTokenVerifier.verify("id-token")).thenReturn(VERIFIED);
        when(userRepository.findByGoogleId("g-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.loginWithGoogle(request("Buyer")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageStartingWith("TOTP_REQUIRED");
        verify(jwtTokenProvider, never()).generateAccessToken(anyString(), anyString(), anyString());
    }

    @Test
    void deactivatedAccountIsRejected() {
        User existing = user(UserRole.ROLE_BUYER);
        existing.setGoogleId("g-123");
        existing.setIsActive(false);
        when(googleTokenVerifier.verify("id-token")).thenReturn(VERIFIED);
        when(userRepository.findByGoogleId("g-123")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.loginWithGoogle(request("Buyer")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void passwordLoginForGoogleOnlyAccountFailsCleanly() {
        User googleOnly = user(UserRole.ROLE_BUYER);
        googleOnly.setPasswordHash(null);
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(googleOnly));

        assertThatThrownBy(() -> service.login(
                LoginRequest.builder().email("buyer@example.com").password("Password@123").build()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
        verify(passwordEncoder, never()).matches(any(), any());
    }
}
