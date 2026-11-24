package seekfactory.axoraa.services.media;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import seekfactory.axoraa.config.OneTimeJobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MediaVariantWarmerTest {

    private final OneTimeJobs jobs = mock(OneTimeJobs.class);

    private MediaVariantWarmer warmer(String mode) {
        return new MediaVariantWarmer(mock(JdbcTemplate.class), mock(MediaVariantService.class), jobs, mode);
    }

    @Test
    void runsOnceByItselfWithNoSetting() {
        when(jobs.isPending(MediaVariantWarmer.JOB, MediaVariantWarmer.VERSION)).thenReturn(true, false);
        assertThat(warmer("auto").shouldRun()).isTrue();    // first start after the deploy
        assertThat(warmer("auto").shouldRun()).isFalse();   // done: never again
    }

    @Test
    void canBeForcedOrSwitchedOff() {
        when(jobs.isPending(anyString(), anyInt())).thenReturn(false);
        assertThat(warmer("true").shouldRun()).isTrue();
        assertThat(warmer(" TRUE ").shouldRun()).isTrue();
        when(jobs.isPending(anyString(), anyInt())).thenReturn(true);
        assertThat(warmer("false").shouldRun()).isFalse();
    }
}
