package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import seekfactory.axoraa.entity.AccountToken;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.AccountTokenPurpose;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.repository.AccountTokenRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.MailService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private AccountTokenRepository tokenRepository;
    @Mock private MailService mailService;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AccountServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AccountServiceImpl(userRepository, tokenRepository, encoder, mailService, "http://app.test/");
        user = User.builder().email("a@b.com").name("Asha").passwordHash(encoder.encode("OldPass@123"))
                .isActive(true).emailVerified(false).build();
        user.setId("u-1");
        lenient().when(userRepository.findById("u-1")).thenReturn(Optional.of(user));
        lenient().when(tokenRepository.save(any(AccountToken.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void changePasswordRequiresCurrentPassword() {
        assertThatThrownBy(() -> service.changePassword("u-1", "wrong", "NewPass@123"))
                .isInstanceOf(BadRequestException.class);

        service.changePassword("u-1", "OldPass@123", "NewPass@123");
        assertThat(encoder.matches("NewPass@123", user.getPasswordHash())).isTrue();
    }

    @Test
    void forgotPasswordForUnknownEmailSendsNothing() {
        when(userRepository.findByEmail("nobody@x.com")).thenReturn(Optional.empty());

        service.requestPasswordReset("nobody@x.com");

        verifyNoInteractions(mailService);
    }

    @Test
    void resetLinkWorksOnceAndStoresOnlyAHash() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        service.requestPasswordReset("a@b.com");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailService).send(eq("a@b.com"), anyString(), body.capture());
        Matcher m = Pattern.compile("http://app\\.test/reset-password\\?token=([A-Za-z0-9_-]+)").matcher(body.getValue());
        assertThat(m.find()).isTrue();
        String raw = m.group(1);

        ArgumentCaptor<AccountToken> saved = ArgumentCaptor.forClass(AccountToken.class);
        verify(tokenRepository).save(saved.capture());
        AccountToken token = saved.getValue();
        assertThat(token.getTokenHash()).isEqualTo(AccountServiceImpl.hash(raw)).isNotEqualTo(raw);
        assertThat(token.getPurpose()).isEqualTo(AccountTokenPurpose.PASSWORD_RESET);

        when(tokenRepository.findByTokenHashAndPurpose(token.getTokenHash(), AccountTokenPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(token));
        service.resetPassword(raw, "Fresh@12345");

        assertThat(encoder.matches("Fresh@12345", user.getPasswordHash())).isTrue();
        assertThat(user.getEmailVerified()).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        // A used token cannot be replayed
        assertThatThrownBy(() -> service.resetPassword(raw, "Other@12345")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        AccountToken token = AccountToken.builder().user(user).purpose(AccountTokenPurpose.EMAIL_VERIFICATION)
                .tokenHash(AccountServiceImpl.hash("abc")).expiresAt(Instant.now().minusSeconds(5)).build();
        when(tokenRepository.findByTokenHashAndPurpose(token.getTokenHash(), AccountTokenPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verifyEmail("abc")).isInstanceOf(BadRequestException.class);
        assertThat(user.getEmailVerified()).isFalse();
    }

    @Test
    void resendIsThrottled() {
        AccountToken recent = AccountToken.builder().user(user).purpose(AccountTokenPurpose.EMAIL_VERIFICATION)
                .tokenHash("h").expiresAt(Instant.now().plusSeconds(3600)).build();
        recent.setCreatedAt(Instant.now().minusSeconds(10));
        when(tokenRepository.findByUserIdAndPurposeAndUsedAtIsNull("u-1", AccountTokenPurpose.EMAIL_VERIFICATION))
                .thenReturn(List.of(recent));

        assertThatThrownBy(() -> service.sendEmailVerification("u-1")).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(mailService);
    }
}
