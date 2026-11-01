package seekfactory.axoraa.services.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class VideoAdmissionTest {
    @Test
    void rejectsNinthJobAndReleasesReservations(@TempDir Path dir) throws Exception {
        try (var transcoder = new VideoTranscoder(dir.resolve("missing").toString(),23,"medium")) {
            CountDownLatch blocked = new CountDownLatch(1);
            CountDownLatch started = new CountDownLatch(1);
            var first = transcoder.submit(dir.resolve("a"),dir.resolve("b"), ignored -> {
                started.countDown();
                try { blocked.await(10,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            assertThat(started.await(2,TimeUnit.SECONDS)).isTrue();
            for(int i=0;i<7;i++) transcoder.submit(dir.resolve("a"),dir.resolve("b"), ignored -> {});
            assertThatThrownBy(transcoder::reserve).isInstanceOf(RejectedExecutionException.class);
            blocked.countDown();
            first.get(5,TimeUnit.SECONDS);
            try(var reservation=transcoder.reserve()) { assertThat(reservation).isNotNull(); }
        }
    }
}
