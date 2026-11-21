package seekfactory.axoraa.services.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import seekfactory.axoraa.services.services.MediaStorageService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HlsPublisherTest {
    private static final String A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    private JdbcTemplate jdbc;
    private MediaStorageService storage;
    private VideoTranscoder transcoder;
    private HlsPublisher publisher;
    private final List<String> uploaded = new ArrayList<>();

    /** Spring itself must be able to create the publisher, or the API does not start with HLS on. */
    @Test
    void springCreatesThePublisherWhenEnabled() {
        new ApplicationContextRunner()
                .withPropertyValues("app.media.hls.enabled=true", "app.media.ffmpeg-path=/nonexistent/ffmpeg")
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(MediaStorageService.class, () -> mock(MediaStorageService.class))
                .withUserConfiguration(HlsPublisher.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(HlsPublisher.class);
                });
    }

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        storage = mock(MediaStorageService.class);
        transcoder = mock(VideoTranscoder.class);
        when(transcoder.isAvailable()).thenReturn(true);
        publisher = new HlsPublisher(jdbc, storage, transcoder);
        doAnswer(inv -> {
            uploaded.add(inv.getArgument(0));
            return null;
        }).when(storage).storePublicFile(anyString(), any(), anyString(), anyString());
        // A successful packaging writes the files ffmpeg would
        when(transcoder.packageHls(any(), any())).thenAnswer(inv -> {
            Path out = inv.getArgument(1);
            Files.createDirectories(out);
            for (String f : new String[]{"master.m3u8", "stream_0.m3u8", "stream_1.m3u8", "stream_0_000.ts", "stream_1_000.ts", "junk.txt"}) {
                Files.writeString(out.resolve(f), f);
            }
            return true;
        });
    }

    private void reels(Map<String, Object>... rows) {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(rows));
    }

    @Test
    void packagesUploadsSegmentsBeforePlaylistsAndLinksTheSeek() {
        reels(Map.of("id", "r1", "video_url", "/api/v1/media/" + A + ".mp4"));

        publisher.publishPending();

        assertThat(uploaded).containsExactly(
                "hls/" + A + "/stream_0_000.ts", "hls/" + A + "/stream_1_000.ts",
                "hls/" + A + "/stream_0.m3u8", "hls/" + A + "/stream_1.m3u8",
                "hls/" + A + "/master.m3u8");
        verify(storage).storePublicFile(eq("hls/" + A + "/stream_0_000.ts"), any(), eq("video/mp2t"), eq(HlsPublisher.SEGMENT_CACHE));
        verify(storage).storePublicFile(eq("hls/" + A + "/master.m3u8"), any(), eq("application/vnd.apple.mpegurl"), eq(HlsPublisher.PLAYLIST_CACHE));
        verify(jdbc).update(anyString(), eq("/api/v1/media/hls/" + A + "/master.m3u8"), eq("r1"), eq("/api/v1/media/" + A + ".mp4"));
    }

    @Test
    void readyCopiesAreLinkedWithoutRepackaging() {
        reels(Map.of("id", "r1", "video_url", "/api/v1/media/" + A + ".mov"));
        when(storage.publicFileExists("hls/" + A + "/master.m3u8")).thenReturn(true);

        publisher.publishPending();

        verify(transcoder, never()).packageHls(any(), any());
        verify(jdbc).update(anyString(), eq("/api/v1/media/hls/" + A + "/master.m3u8"), eq("r1"), anyString());
    }

    @Test
    void skipsLinkedProcessingAndForeignVideos() {
        java.util.HashMap<String, Object> linked = new java.util.HashMap<>(Map.of("id", "r1", "video_url", "/api/v1/media/" + A + ".mp4"));
        linked.put("hls_url", "/api/v1/media/hls/" + A + "/master.m3u8");
        reels(linked,
                Map.of("id", "r2", "video_url", "/api/v1/media/" + B + ".mp4"),
                Map.of("id", "r3", "video_url", "https://example.com/clip.mp4"));
        when(storage.isProcessing(B + ".mp4")).thenReturn(true);

        publisher.publishPending();

        verify(transcoder, never()).packageHls(any(), any());
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test
    void aFailedVideoIsNotRetriedUntilRestartAndTheSeekKeepsItsFile() {
        reels(Map.of("id", "r1", "video_url", "/api/v1/media/" + A + ".mp4"));
        doReturn(false).when(transcoder).packageHls(any(), any());

        publisher.publishPending();
        publisher.publishPending();

        verify(transcoder, times(1)).packageHls(any(), any());
        verify(jdbc, never()).update(anyString(), any(), any(), any());
        assertThat(uploaded).isEmpty();
    }

    @Test
    void atMostAFewVideosArePackagedPerRun() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            rows.add(Map.of("id", "r" + i, "video_url", "/api/v1/media/" + String.format("%08d", i) + "-aaaa-aaaa-aaaa-aaaaaaaaaaaa.mp4"));
        }
        when(jdbc.queryForList(anyString())).thenReturn(rows);

        publisher.publishPending();

        verify(transcoder, times(HlsPublisher.VIDEOS_PER_RUN)).packageHls(any(), any());
    }
}
