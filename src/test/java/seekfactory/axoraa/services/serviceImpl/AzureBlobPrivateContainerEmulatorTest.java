package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import seekfactory.axoraa.services.media.AzureBlobClient;
import seekfactory.axoraa.services.media.ImageCompressor;
import seekfactory.axoraa.services.media.MediaTypes;
import seekfactory.axoraa.services.media.VideoTranscoder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Attachments in a separate private container, with old attachments still read from the public one.
 * Runs against a local Azurite emulator (containers "media" and "private" created beforehand); never a cloud account.
 */
@EnabledIfSystemProperty(named = "seekfactory.azurite", matches = "true")
class AzureBlobPrivateContainerEmulatorTest {
    private static final String KEY = "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";
    private static final String ENDPOINT = "http://127.0.0.1:10000/devstoreaccount1";

    @Test
    void newAttachmentsGoToThePrivateContainerAndOldOnesStillOpen(@TempDir Path work) throws Exception {
        var media = new AzureBlobClient("devstoreaccount1", KEY, "media", ENDPOINT);
        var privateBlobs = new AzureBlobClient("devstoreaccount1", KEY, "private", ENDPOINT);
        var service = new AzureBlobMediaStorageService(media, privateBlobs, "", Duration.ofHours(1),
                new MediaTypes.Limits(10_000_000, 10_000_000, 10_000_000), new ImageCompressor(2048, 0.85f),
                new VideoTranscoder("ffmpeg-not-installed-in-tests", 23, "veryfast"), work);

        // New upload: only in the private container
        String key = service.storePrivate("conv-1",
                new MockMultipartFile("file", "spec.pdf", "application/pdf", "%PDF-1.4 new".getBytes(StandardCharsets.UTF_8)));
        assertThat(privateBlobs.head("conv-1/" + key)).isPresent();
        assertThat(media.head("private/conv-1/" + key)).isEmpty();
        assertThat(service.loadPrivate("conv-1", key).getInputStream().readAllBytes())
                .isEqualTo("%PDF-1.4 new".getBytes(StandardCharsets.UTF_8));
        assertThat(service.privateUrl("conv-1", key).orElseThrow()).contains("/private/conv-1/" + key);

        // Attachment stored before the switch, under private/ in the public container
        String old = "22222222-2222-2222-2222-222222222222.pdf";
        media.put("private/conv-1/" + old, "application/pdf", null, "%PDF-1.4 old".getBytes(StandardCharsets.UTF_8));
        assertThat(service.loadPrivate("conv-1", old).getInputStream().readAllBytes())
                .isEqualTo("%PDF-1.4 old".getBytes(StandardCharsets.UTF_8));
        assertThat(service.privateUrl("conv-1", old).orElseThrow()).contains("/media/private/conv-1/" + old);
    }
}
