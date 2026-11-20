package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import seekfactory.axoraa.services.media.AzureBlobClient;
import seekfactory.axoraa.services.media.ImageCompressor;
import seekfactory.axoraa.services.media.MediaTypes;
import seekfactory.axoraa.services.media.VideoTranscoder;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Public link building only: no request leaves the test (the client signs links locally). */
class AzureBlobMediaStorageServiceTest {
    private static final String KEY = "11111111-1111-1111-1111-111111111111.jpg";
    private static final String KEY_BASE64 = java.util.Base64.getEncoder().encodeToString(new byte[32]);

    private static AzureBlobMediaStorageService storage(String publicBaseUrl, Path work) {
        var blobs = new AzureBlobClient("acct", KEY_BASE64, "media", "https://acct.blob.core.windows.net");
        return new AzureBlobMediaStorageService(blobs, null, publicBaseUrl, Duration.ofHours(1),
                new MediaTypes.Limits(1, 1, 1), new ImageCompressor(2048, 0.85f),
                new VideoTranscoder("ffmpeg-not-installed-in-tests", 23, "veryfast"), work);
    }

    @Test
    void withAPublicBaseUrlFilesAndCopiesGetPermanentAddresses(@TempDir Path work) {
        var service = storage("https://acct.blob.core.windows.net/media/", work);

        assertThat(service.publicUrl(KEY)).contains("https://acct.blob.core.windows.net/media/media/" + KEY);
        assertThat(service.variantUrl("11111111-1111-1111-1111-111111111111-w640.jpg"))
                .contains("https://acct.blob.core.windows.net/media/media/variants/11111111-1111-1111-1111-111111111111-w640.jpg");
        assertThat(service.publicLinksAreStable()).isTrue();
        assertThat(service.publicRedirectMaxAgeSeconds()).isEqualTo(AzureBlobMediaStorageService.STABLE_REDIRECT_MAX_AGE_SECONDS);
        // The same address every time: browsers and any CDN can keep their copy
        assertThat(service.publicUrl(KEY)).isEqualTo(service.publicUrl(KEY));
    }

    @Test
    void withoutOneFilesKeepExpiringSignedLinks(@TempDir Path work) {
        var service = storage("", work);

        String link = service.publicUrl(KEY).orElseThrow();
        assertThat(link).startsWith("https://acct.blob.core.windows.net/media/media/" + KEY + "?").contains("sig=");
        assertThat(service.publicLinksAreStable()).isFalse();
        assertThat(service.publicRedirectMaxAgeSeconds()).isBetween(1L, 300L);
    }

    @Test
    void aCdnHostWorksTheSameWay(@TempDir Path work) {
        var service = storage("https://media.seekfactory.com", work);
        assertThat(service.publicUrl(KEY)).contains("https://media.seekfactory.com/media/" + KEY);
    }
}
