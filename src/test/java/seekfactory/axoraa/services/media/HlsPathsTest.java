package seekfactory.axoraa.services.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HlsPathsTest {
    private static final String ID = "9c052948-f232-4104-8de5-46dad6a5bf6c";

    @Test
    void onlyTheFixedStreamingFilesAreAccepted() {
        assertThat(HlsPaths.isValid(ID, "master.m3u8")).isTrue();
        assertThat(HlsPaths.isValid(ID, "index.m3u8")).isTrue();
        assertThat(HlsPaths.isValid(ID, "v2_2.m3u8")).isTrue();
        assertThat(HlsPaths.isValid(ID, "v2_0_007.ts")).isTrue();
        assertThat(HlsPaths.isValid(ID, "stream_0.m3u8")).isTrue();
        assertThat(HlsPaths.isValid(ID, "stream_1_000.ts")).isTrue();
        assertThat(HlsPaths.isValid(ID, "stream_1_12345.ts")).isTrue();

        for (String file : new String[]{"../master.m3u8", "stream_9.m3u8", "stream_0_1.ts", "index.html",
                "stream_0.m3u8/../../x", "master.m3u8.exe", "", "stream_0_000.mp4", "v2_9.m3u8", "v3_0.m3u8", "index.m3u8.bak"}) {
            assertThat(HlsPaths.isValid(ID, file)).as(file).isFalse();
        }
        assertThat(HlsPaths.isValid("../../private", "master.m3u8")).isFalse();
        assertThat(HlsPaths.isValid(ID.toUpperCase(), "master.m3u8")).isFalse();
        assertThatThrownBy(() -> HlsPaths.path(ID, "../x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pathsAndAddresses() {
        assertThat(HlsPaths.path(ID, "stream_0_001.ts")).isEqualTo("hls/" + ID + "/stream_0_001.ts");
        assertThat(HlsPaths.masterUrl(ID)).isEqualTo("/api/v1/media/hls/" + ID + "/index.m3u8");
        assertThat(HlsPaths.legacyMasterUrl(ID)).isEqualTo("/api/v1/media/hls/" + ID + "/master.m3u8");
        assertThat(HlsPaths.videoId("/api/v1/media/" + ID + ".mov")).contains(ID);
        assertThat(HlsPaths.videoKey("/api/v1/media/" + ID + ".mp4")).contains(ID + ".mp4");
        assertThat(HlsPaths.videoId("https://cdn.example/clip.mp4")).isEmpty();
        assertThat(HlsPaths.videoId(null)).isEmpty();
        assertThat(HlsPaths.contentTypeOf("master.m3u8")).isEqualTo("application/vnd.apple.mpegurl");
        assertThat(HlsPaths.contentTypeOf("stream_0_000.ts")).isEqualTo("video/mp2t");
        assertThat(HlsPaths.isImmutable("stream_0_000.ts")).isTrue();
        assertThat(HlsPaths.isImmutable("master.m3u8")).isFalse();
    }

    @Test
    void aStreamIsOnlyHandedOutForTheSeeksCurrentVideo() {
        String video = "/api/v1/media/" + ID + ".mp4";
        String master = HlsPaths.masterUrl(ID);
        assertThat(HlsPaths.currentFor(video, master)).isEqualTo(master);
        // A v1 copy keeps playing until the v2 copy replaces it
        assertThat(HlsPaths.currentFor(video, HlsPaths.legacyMasterUrl(ID))).isEqualTo(HlsPaths.legacyMasterUrl(ID));
        // The seller replaced the video: the old stream must not play for the new one
        assertThat(HlsPaths.currentFor("/api/v1/media/11111111-1111-1111-1111-111111111111.mp4", master)).isNull();
        assertThat(HlsPaths.currentFor(video, null)).isNull();
        assertThat(HlsPaths.currentFor(video, "")).isNull();
    }
}
