package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import seekfactory.axoraa.config.JwtConfig;
import seekfactory.axoraa.dto.Request.auth.LoginRequest;
import seekfactory.axoraa.dto.Response.auth.AuthResponse;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.AuthProvider;
import seekfactory.axoraa.enums.UserRole;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.AccountService;
import seekfactory.axoraa.utils.JwtTokenProvider;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** A supplier's first sign-in is flagged so the frontend can open the product catalog. */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplFirstLoginTest {

    @Mock private UserRepository userRepository;
    @Mock private ManufacturerRepository manufacturerRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private JwtConfig jwtConfig;
    @Mock private AccountService accountService;
    @Mock private seekfactory.axoraa.services.services.PresenceService presenceService;

    @InjectMocks private AuthServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().email("new.factory@example.com").name("New Factory").passwordHash("hash")
                .role(UserRole.ROLE_SUPPLIER).authProvider(AuthProvider.LOCAL).isActive(true).build();
        user.setId("u-1");
        when(userRepository.findByEmail("new.factory@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password@123", "hash")).thenReturn(true);
        lenient().when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(jwtTokenProvider.generateAccessToken(anyString(), anyString(), anyString())).thenReturn("a");
        lenient().when(jwtTokenProvider.generateRefreshToken(anyString(), anyString(), anyString())).thenReturn("r");
    }

    @Test
    void onlyTheFirstSignInIsFlagged() {
        LoginRequest request = LoginRequest.builder().email("new.factory@example.com").password("Password@123").build();

        AuthResponse first = service.login(request);
        assertThat(first.isFirstLogin()).isTrue();
        assertThat(user.getLastLoginAt()).isNotNull();

        AuthResponse second = service.login(request);
        assertThat(second.isFirstLogin()).isFalse();
    }

    @Test
    void accountThatSignedInBeforeIsNotFlagged() {
        user.setLastLoginAt(Instant.parse("2026-09-01T10:00:00Z"));

        AuthResponse response = service.login(
                LoginRequest.builder().email("new.factory@example.com").password("Password@123").build());

        assertThat(response.isFirstLogin()).isFalse();
        assertThat(user.getLastLoginAt()).isAfter(Instant.parse("2026-09-01T10:00:00Z"));
    }
}
