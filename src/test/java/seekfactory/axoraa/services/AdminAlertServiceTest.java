package seekfactory.axoraa.services;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.services.services.MailService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminAlertServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MailService mail = mock(MailService.class);
    private final AdminAlertService service = new AdminAlertService(jdbc, mail, "https://www.seekfactory.com/");

    @SuppressWarnings("unchecked")
    private void saved(AdminAlertService.Settings s) {
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(AdminAlertService.KEY))).thenReturn(s);
    }

    private static AdminAlertService.Settings settings(boolean on, List<String> to, boolean newUser, boolean newMfr) {
        return new AdminAlertService.Settings(on, to, newUser, newMfr, true, true, null);
    }

    @Test
    void emailsEveryRecipientForAnEventThatIsOn() {
        saved(settings(true, List.of("a@x.com", "b@x.com"), true, true));

        service.newManufacturer("Acme Machines", "acme@example.cn", null, "China");

        verify(mail).send(eq("a@x.com"), contains("Manufacturer waiting for approval: Acme Machines"), contains("https://www.seekfactory.com/admin/manufacturers"));
        verify(mail).send(eq("b@x.com"), anyString(), anyString());
    }

    @Test
    void nothingWhenOffOrThatEventIsOffOrNoRecipients() {
        saved(settings(false, List.of("a@x.com"), true, true));
        service.newUser("Bob", "bob@example.com", null, "India");

        saved(settings(true, List.of("a@x.com"), false, true));
        service.newUser("Bob", "bob@example.com", null, "India");

        saved(settings(true, List.of(), true, true));
        service.newManufacturer("Acme", null, "+86 138", "China");

        verifyNoInteractions(mail);
    }

    @Test
    void userTextCannotBreakTheSubjectLine() {
        saved(settings(true, List.of("a@x.com"), true, true));
        service.newContent("product", "Pump\r\nBcc: evil@x.com", "Acme");
        verify(mail).send(eq("a@x.com"), argThat(subject -> !subject.contains("\n") && !subject.contains("\r")), anyString());
    }

    @Test
    void recipientsAreCheckedAndCleaned() {
        assertThat(AdminAlertService.parseRecipients(" A@X.com, b@x.com;a@x.com ")).containsExactly("a@x.com", "b@x.com");
        assertThatThrownBy(() -> AdminAlertService.cleanRecipients(List.of("not-an-email"))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.updateSettings(settings(true, List.of(), true, true), "admin"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void aMailProblemNeverReachesTheCaller() {
        saved(settings(true, List.of("a@x.com"), true, true));
        doThrow(new RuntimeException("smtp down")).when(mail).send(anyString(), anyString(), anyString());
        service.newPayment("Bob", "Pro", "INR 999");   // no exception
    }
}
