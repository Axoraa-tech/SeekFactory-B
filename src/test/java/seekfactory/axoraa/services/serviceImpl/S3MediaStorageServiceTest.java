package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import seekfactory.axoraa.dto.Response.media.MediaUploadResponse;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.services.media.ImageCompressor;
import seekfactory.axoraa.services.media.MediaTypes;
import seekfactory.axoraa.services.media.VideoTranscoder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3MediaStorageServiceTest {

    private static final MediaTypes.Limits LIMITS = new MediaTypes.Limits(20 << 20, 100 << 20, 50 << 20);

    @TempDir Path work;
    private S3Client s3;
    private S3Presigner presigner;

    @BeforeEach
    void setUp() {
        s3 = mock(S3Client.class);
        presigner = S3Presigner.builder()
                .region(Region.AWS_GLOBAL)
                .endpointOverride(URI.create("https://s3.oss-ap-southeast-1.aliyuncs.com"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("AKID", "SECRET")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(false).build())
                .build();
    }

    @AfterEach
    void tearDown() {
        presigner.close();
    }

    private S3MediaStorageService service(String publicBaseUrl) {
        return new S3MediaStorageService(s3, presigner,
                new S3MediaStorageService.Settings("seek-media", publicBaseUrl, Duration.ofMinutes(10)),
                LIMITS, new ImageCompressor(2048, 0.85f),
                new VideoTranscoder(work.resolve("no-ffmpeg").toString(), 23, "veryfast"), work);
    }

    private PutObjectRequest lastPut() {
        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(put.capture(), any(RequestBody.class));
        return put.getValue();
    }

    @Test
    void publicUploadGoesUnderMediaPrefixAndKeepsTheApiPath() {
        MediaUploadResponse res = service("https://media.example.com/").store(
                new MockMultipartFile("file", "spec.pdf", "application/pdf", new byte[]{1, 2, 3}), "document");

        String key = res.getUrl().substring(MediaTypes.PUBLIC_PATH.length());
        assertThat(res.getUrl()).startsWith("/api/v1/media/").endsWith(".pdf");
        PutObjectRequest put = lastPut();
        assertThat(put.bucket()).isEqualTo("seek-media");
        assertThat(put.key()).isEqualTo("media/" + key);
        assertThat(put.contentType()).isEqualTo("application/pdf");
        assertThat(put.cacheControl()).contains("immutable");
    }

    @Test
    void withoutFfmpegVideosAreUploadedAsFinal() {
        MediaUploadResponse res = service("").store(
                new MockMultipartFile("file", "clip.webm", "video/webm", new byte[]{1, 2, 3}), "video");

        assertThat(res.getUrl()).endsWith(".webm");
        assertThat(lastPut().contentType()).isEqualTo("video/webm");
        assertThat(lastPut().cacheControl()).contains("immutable");
    }

    @Test
    void finishedVideoReplacesTheOriginalAndCleansUp() throws Exception {
        S3MediaStorageService storage = service("");
        Path source = Files.writeString(work.resolve("pending/11111111-1111-1111-1111-111111111111.mov"), "original");
        Path target = Files.writeString(work.resolve("out/11111111-1111-1111-1111-111111111111.mp4"), "small");
        assertThat(storage.isProcessing("11111111-1111-1111-1111-111111111111.mp4")).isTrue();

        storage.finishVideo(source, target, "11111111-1111-1111-1111-111111111111.mp4", true);

        PutObjectRequest put = lastPut();
        assertThat(put.key()).isEqualTo("media/11111111-1111-1111-1111-111111111111.mp4");
        assertThat(put.contentType()).isEqualTo("video/mp4");
        assertThat(put.cacheControl()).contains("immutable");
        assertThat(source).doesNotExist();
        assertThat(target).doesNotExist();
        assertThat(storage.isProcessing("11111111-1111-1111-1111-111111111111.mp4")).isFalse();
    }

    @Test
    void publicUrlPointsAtTheCdn() {
        assertThat(service("https://media.example.com/").publicUrl("11111111-1111-1111-1111-111111111111.jpg"))
                .contains("https://media.example.com/media/11111111-1111-1111-1111-111111111111.jpg");
    }

    @Test
    void publicUrlFallsBackToASignedLinkWithoutACdn() {
        String url = service("").publicUrl("11111111-1111-1111-1111-111111111111.jpg").orElseThrow();
        assertThat(url).startsWith("https://seek-media.s3.oss-ap-southeast-1.aliyuncs.com/media/11111111-1111-1111-1111-111111111111.jpg?")
                .contains("X-Amz-Signature=");
    }

    @Test
    void publicUrlRejectsKeysOutsideTheAllowlist() {
        assertThatThrownBy(() -> service("https://media.example.com").publicUrl("../private/x/secret.pdf"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void privateUploadIsNamespacedAndNotPubliclyCacheable() {
        String key = service("https://media.example.com").storePrivate("conv-1",
                new MockMultipartFile("file", "drawing.step", "application/octet-stream", new byte[]{1}));

        PutObjectRequest put = lastPut();
        assertThat(put.key()).isEqualTo("private/conv-1/" + key);
        assertThat(put.cacheControl()).isNull();
    }

    @Test
    void privateNamespaceMustBeSafe() {
        assertThatThrownBy(() -> service("").storePrivate("../media",
                new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1})))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void privateUrlIsShortLivedAndSetsDisposition() {
        S3MediaStorageService storage = service("https://media.example.com");

        String pdf = storage.privateUrl("conv-1", "11111111-1111-1111-1111-111111111111.pdf").orElseThrow();
        String step = storage.privateUrl("conv-1", "11111111-1111-1111-1111-111111111111.step").orElseThrow();

        // Signed against the bucket, never the public CDN
        assertThat(pdf).startsWith("https://seek-media.s3.oss-ap-southeast-1.aliyuncs.com/private/conv-1/")
                .contains("X-Amz-Expires=600")
                .contains("response-content-disposition=inline")
                .contains("response-content-type=application%2Fpdf");
        assertThat(step).contains("response-content-disposition=attachment");
    }

    @Test
    void missingPrivateFileIsNotFound() {
        when(s3.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build());

        assertThatThrownBy(() -> service("").loadPrivate("conv-1", "11111111-1111-1111-1111-111111111111.pdf"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void existingPrivateFileLoads() throws Exception {
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentLength(42L).build());

        assertThat(service("").loadPrivate("conv-1", "11111111-1111-1111-1111-111111111111.pdf").contentLength()).isEqualTo(42L);
    }

    @Test
    void deleteRemovesOnlyValidPublicKeys() {
        S3MediaStorageService storage = service("");

        storage.delete("not-a-key");
        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));

        storage.delete("11111111-1111-1111-1111-111111111111.mp4");
        ArgumentCaptor<DeleteObjectRequest> delete = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(delete.capture());
        assertThat(delete.getValue().key()).isEqualTo("media/11111111-1111-1111-1111-111111111111.mp4");
    }

    @Test
    void migrationCopiesPublicPendingAndPrivateFilesOnce(@TempDir Path uploads) throws Exception {
        Files.writeString(uploads.resolve("22222222-2222-2222-2222-222222222222.jpg"), "photo");
        Files.writeString(uploads.resolve("README.txt"), "ignored");
        Files.createDirectories(uploads.resolve("pending"));
        Files.writeString(uploads.resolve("pending/33333333-3333-3333-3333-333333333333.mov"), "raw");
        Files.createDirectories(uploads.resolve("private/conv-1"));
        Files.writeString(uploads.resolve("private/conv-1/44444444-4444-4444-4444-444444444444.pdf"), "doc");
        when(s3.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build());

        int uploaded = new MediaMigrationRunner(service(""), uploads.toString()).migrate(uploads);

        assertThat(uploaded).isEqualTo(3);
        ArgumentCaptor<PutObjectRequest> puts = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3, org.mockito.Mockito.times(3)).putObject(puts.capture(), any(RequestBody.class));
        assertThat(puts.getAllValues()).extracting(PutObjectRequest::key).containsExactlyInAnyOrder(
                "media/22222222-2222-2222-2222-222222222222.jpg",
                "media/33333333-3333-3333-3333-333333333333.mp4",
                "private/conv-1/44444444-4444-4444-4444-444444444444.pdf");
    }
}
