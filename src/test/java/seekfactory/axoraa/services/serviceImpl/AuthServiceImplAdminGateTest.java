package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import seekfactory.axoraa.config.JwtConfig;
import seekfactory.axoraa.dto.Request.auth.LoginRequest;
import seekfactory.axoraa.dto.Request.auth.PhoneLoginRequest;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.AuthProvider;
import seekfactory.axoraa.enums.UserRole;
import seekfactory.axoraa.exceptions.ForbiddenException;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.AccountService;
import seekfactory.axoraa.utils.JwtTokenProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Admins cannot skip the authenticator step by using the buyer/supplier sign-in. */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplAdminGateTest {

    @Mock private UserRepository userRepository;
    @Mock private ManufacturerRepository manufacturerRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private JwtConfig jwtConfig;
    @Mock private AccountService accountService;
    @Mock private seekfactory.axoraa.services.services.PresenceService presenceService;

    @InjectMocks private AuthServiceImpl service;

    private User admin() {
        User admin = User.builder().email("admin@example.com").phone("+919999999999").name("Admin")
                .passwordHash("hash").role(UserRole.ROLE_ADMIN).authProvider(AuthProvider.LOCAL)
                .isActive(true).build();
        admin.setId("a-1");
        return admin;
    }

    @Test
    void emailLoginForAdminRequiresTotpAndIssuesNoTokens() {
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin()));
        when(passwordEncoder.matches("Password@123", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(
                LoginRequest.builder().email("admin@example.com").password("Password@123").build()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageStartingWith("TOTP_REQUIRED");
        verify(jwtTokenProvider, never()).generateAccessToken(anyString(), anyString(), anyString());
    }

    @Test
    void phoneLoginForAdminRequiresTotp() {
        ReflectionTestUtils.setField(service, "mockOtp", "123456");
        when(userRepository.findByPhone("+919999999999")).thenReturn(Optional.of(admin()));
        PhoneLoginRequest request = new PhoneLoginRequest();
        request.setPhone("+919999999999");
        request.setOtp("123456");
        request.setRole("Buyer");

        assertThatThrownBy(() -> service.loginWithPhone(request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageStartingWith("TOTP_REQUIRED");
        verify(jwtTokenProvider, never()).generateAccessToken(anyString(), anyString(), anyString());
    }
}
