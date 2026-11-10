package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import seekfactory.axoraa.dto.Response.media.MediaUploadResponse;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.services.media.AzureBlobClient;
import seekfactory.axoraa.services.media.ImageCompressor;
import seekfactory.axoraa.services.media.MediaTypes;
import seekfactory.axoraa.services.media.VideoTranscoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Certificate documents (kind=document, as used by custom certificates) stored in and read back
 * from Blob Storage through the real Azure storage service. Runs only against a local Azurite
 * emulator with its well-known dev account (needs a "media" container); never a cloud account.
 */
@EnabledIfSystemProperty(named = "seekfactory.azurite", matches = "true")
class AzureCertificateDocumentEmulatorTest {
    private static final String KEY = "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

    @TempDir Path work;

    @Test
    void certificatePdfRoundTripsThroughBlobStorage() throws Exception {
        var blobs = new AzureBlobClient("devstoreaccount1", KEY, "media", "http://127.0.0.1:10000/devstoreaccount1");
        try (var transcoder = new VideoTranscoder(work.resolve("no-ffmpeg").toString(), 23, "medium")) {
            var storage = new AzureBlobMediaStorageService(blobs, Duration.ofMinutes(10),
                    new MediaTypes.Limits(20L << 20, 100L << 20, 50L << 20),
                    new ImageCompressor(2048, 0.85f), transcoder, work);

            byte[] pdf = "%PDF-1.4\n% ISO 13485:2016 certificate\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
            MediaUploadResponse uploaded = storage.store(
                    new MockMultipartFile("file", "iso-13485.pdf", "application/pdf", pdf), "document");

            // The saved reference is our own media path, which custom certificates require.
            assertThat(uploaded.getUrl()).matches("^/api/v1/media/[0-9a-f-]{36}\\.pdf$");
            String key = uploaded.getUrl().substring(MediaTypes.PUBLIC_PATH.length());

            assertThat(storage.load(key).contentLength()).isEqualTo(pdf.length);
            assertThat(storage.contentTypeOf(key)).isEqualTo("application/pdf");

            String link = storage.publicUrl(key).orElseThrow();
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(link)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(pdf);

            // Unsupported document types never reach Blob Storage.
            assertThatThrownBy(() -> storage.store(
                    new MockMultipartFile("file", "cert.exe", "application/octet-stream", new byte[]{1, 2}), "document"))
                    .isInstanceOf(BadRequestException.class);
            storage.shutdown();
        }
    }
}
