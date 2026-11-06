package seekfactory.axoraa.services.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Signs real requests against a local Azurite emulator (well-known dev account); never a cloud account. */
@EnabledIfSystemProperty(named = "seekfactory.azurite", matches = "true")
class AzureBlobClientEmulatorTest {
    private static final String KEY = "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

    @Test
    void putHeadGetSignedLinkAndDelete() throws Exception {
        var blobs = new AzureBlobClient("devstoreaccount1", KEY, "media", "http://127.0.0.1:10000/devstoreaccount1");
        byte[] body = "hello blob".getBytes(StandardCharsets.UTF_8);
        blobs.put("media/test-1.txt", "text/plain", "public, max-age=60", body);

        var head = blobs.head("media/test-1.txt").orElseThrow();
        assertThat(head.contentLength()).isEqualTo(body.length);
        assertThat(head.contentType()).isEqualTo("text/plain");
        assertThat(blobs.getBytes("media/test-1.txt")).isEqualTo(body);

        String link = blobs.readUrl("media/test-1.txt", Duration.ofMinutes(5), null, "attachment");
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(link)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(body);
        assertThat(response.headers().firstValue("Content-Disposition")).hasValue("attachment");

        String tampered = link.replace("sp=r", "sp=rw");
        var rejected = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(tampered)).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(rejected.statusCode()).isEqualTo(403);

        blobs.delete("media/test-1.txt");
        assertThat(blobs.head("media/test-1.txt")).isEmpty();
    }
}
