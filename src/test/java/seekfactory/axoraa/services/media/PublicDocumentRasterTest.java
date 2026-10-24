package seekfactory.axoraa.services.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;
import seekfactory.axoraa.exceptions.BadRequestException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static seekfactory.axoraa.services.media.DocumentRasterFixtures.avif;
import static seekfactory.axoraa.services.media.DocumentRasterFixtures.webp;

class PublicDocumentRasterTest {
    private static final MediaTypes.Limits LIMITS = new MediaTypes.Limits(20 << 20, 100 << 20, 50 << 20);

    static Stream<DocumentRasterFixtures.Scan> scans() throws IOException { return DocumentRasterFixtures.scans(); }

    @ParameterizedTest
    @MethodSource("scans")
    void acceptsScansAsDocumentsWithoutChangingTheInput(DocumentRasterFixtures.Scan scan) throws IOException {
        var file = new MockMultipartFile("file", "SCAN." + scan.extension().toUpperCase(), scan.contentType().toUpperCase(), scan.bytes());
        var accepted = MediaTypes.validatePublic(file, "document", LIMITS);
        assertThat(accepted.kind()).isEqualTo("document");
        assertThat(accepted.isImage()).isFalse();
        assertThat(accepted.extension()).isEqualTo(scan.extension().equals("jpeg") ? "jpg" : scan.extension());
        assertThat(file.getBytes()).isEqualTo(scan.bytes());
    }

    @Test
    void rejectsMissingWrongOrGenericMimeAndSpoofedSignatures() {
        byte[] avif = avif("mif1", "avif");
        for (String mime : Arrays.asList(null, "application/octet-stream", "image/png", "text/html")) {
            assertThatThrownBy(() -> validate("scan.avif", mime, avif)).isInstanceOf(BadRequestException.class);
        }
        for (String ext : new String[]{"png", "jpg", "jpeg", "gif", "webp", "avif"}) {
            String mime = ext.equals("jpg") || ext.equals("jpeg") ? "image/jpeg" : "image/" + ext;
            assertThatThrownBy(() -> validate("scan." + ext, mime, "<svg onload='alert(1)'/>".getBytes())).isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> validate("scan." + ext, mime, new byte[]{1})).isInstanceOf(BadRequestException.class);
        }
        assertThatThrownBy(() -> validate("scan.png", "image/png", avif)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> validate("scan.svg", "image/svg+xml", avif)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> validate("avif", "image/avif", avif)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> validate("scan.", "image/avif", avif)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void enforcesBothSizeLimitsBeforeOpeningTheStream() {
        var file = new MockMultipartFile("file", "scan.avif", "image/avif", avif("avif", "mif1")) {
            @Override public long getSize() { return 21L << 20; }
            @Override public java.io.InputStream getInputStream() { throw new AssertionError("Must reject before reading"); }
        };
        assertThatThrownBy(() -> MediaTypes.validatePublic(file, "document", LIMITS)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> MediaTypes.validatePublic(file, "document", new MediaTypes.Limits(50L << 20, 100L << 20, 20L << 20))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void checksWebpContainerLengthAndEncoding() {
        for (String chunk : new String[]{"VP8 ", "VP8L", "VP8X"}) validate("scan.webp", "image/webp", webp(chunk));
        byte[] incorrectLength = webp("VP8L");
        incorrectLength[4]++;
        assertThatThrownBy(() -> validate("scan.webp", "image/webp", incorrectLength)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> validate("scan.webp", "image/webp", webp("JUNK"))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsAnUnreadableScan() {
        var file = new MockMultipartFile("file", "scan.avif", "image/avif", avif("avif", "mif1")) {
            @Override public java.io.InputStream getInputStream() throws IOException { throw new IOException("unreadable"); }
        };
        assertThatThrownBy(() -> MediaTypes.validatePublic(file, "document", LIMITS)).isInstanceOf(BadRequestException.class).hasMessage("Cannot read document image");
    }

    @Test
    void checksAvifBrandsAndBoundsWithoutConfusingMinorVersionOrHeicWithAvif() {
        validate("scan.avif", "image/avif", avif("avif", "mif1"));
        validate("scan.avif", "image/avif", avif("mif1", "avif"));
        validate("scan.avif", "image/avif", avif("avis", "mif1"));
        byte[] minorVersionOnly = avif("mif1", "heic");
        ByteBuffer.wrap(minorVersionOnly, 12, 4).put(new byte[]{'a', 'v', 'i', 'f'});
        assertThatThrownBy(() -> validate("scan.avif", "image/avif", minorVersionOnly)).isInstanceOf(BadRequestException.class);
        for (int boxSize : new int[]{0, 1, 12, 19, 28, Integer.MAX_VALUE, -1}) {
            byte[] invalid = avif("avif", "mif1");
            ByteBuffer.wrap(invalid).putInt(boxSize);
            assertThatThrownBy(() -> validate("scan.avif", "image/avif", invalid)).isInstanceOf(BadRequestException.class);
        }
        byte[] outsideBrand = avif("mif1", "heic");
        ByteBuffer.wrap(outsideBrand, 24, 4).put(new byte[]{'a', 'v', 'i', 'f'});
        assertThatThrownBy(() -> validate("scan.avif", "image/avif", outsideBrand)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void preservesExistingDocumentAndPrivateAttachmentBehavior() {
        assertThat(validate("spec.pdf", "application/octet-stream", new byte[]{1}).extension()).isEqualTo("pdf");
        assertThat(MediaTypes.validatePrivate(new MockMultipartFile("file", "scan.png", "image/png", new byte[]{1}), LIMITS).isImage()).isTrue();
        assertThatThrownBy(() -> MediaTypes.validatePrivate(new MockMultipartFile("file", "scan.avif", "image/avif", avif("avif", "mif1")), LIMITS)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> MediaTypes.validatePublic(new MockMultipartFile("file", "scan.avif", "image/avif", avif("avif", "mif1")), "image", LIMITS)).isInstanceOf(BadRequestException.class);
        assertThat(MediaTypes.KEY_PATTERN.matcher("11111111-1111-1111-1111-111111111111.avif").matches()).isTrue();
        assertThat(MediaTypes.PRIVATE_KEY_PATTERN.matcher("11111111-1111-1111-1111-111111111111.avif").matches()).isFalse();
        assertThat(MediaTypes.contentTypeOf("scan.avif")).isEqualTo("image/avif");
    }

    private static MediaTypes.Accepted validate(String name, String mime, byte[] bytes) {
        return MediaTypes.validatePublic(new MockMultipartFile("file", name, mime, bytes), "document", LIMITS);
    }
}
