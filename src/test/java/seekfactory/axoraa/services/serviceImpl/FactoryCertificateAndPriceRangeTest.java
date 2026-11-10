package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;
import seekfactory.axoraa.dto.common.FactoryCertificate;
import seekfactory.axoraa.exceptions.BadRequestException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Custom certificate validation and product price range rules. */
class FactoryCertificateAndPriceRangeTest {

    private static final String DOC = "/api/v1/media/0b5e7d1c-2f4a-4c1e-9a3b-7d6e5f4a3b2c.pdf";

    // ---- price range ----

    @Test
    void singlePriceHasNoMaximum() {
        assertThat(FactoryServiceImpl.priceMax(new BigDecimal("100"), null)).isNull();
    }

    @Test
    void maximumEqualToMinimumIsASinglePrice() {
        assertThat(FactoryServiceImpl.priceMax(new BigDecimal("100"), new BigDecimal("100.00"))).isNull();
    }

    @Test
    void validRangeKeepsMaximum() {
        assertThat(FactoryServiceImpl.priceMax(new BigDecimal("100"), new BigDecimal("250")))
                .isEqualByComparingTo("250");
    }

    @Test
    void maximumBelowMinimumIsRejected() {
        assertThatThrownBy(() -> FactoryServiceImpl.priceMax(new BigDecimal("100"), new BigDecimal("99.99")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("less than the minimum");
    }

    @Test
    void negativeMaximumIsRejected() {
        assertThatThrownBy(() -> FactoryServiceImpl.priceMax(BigDecimal.ONE, new BigDecimal("-1")))
                .isInstanceOf(BadRequestException.class);
    }

    // ---- certificates ----

    private static FactoryCertificate.FactoryCertificateBuilder custom() {
        return FactoryCertificate.builder().title("  ISO 13485:2016 ").imageUrl(DOC).custom(true)
                .documentName("iso-13485.pdf");
    }

    @Test
    void customCertificateNeedsNoIssuerAndIsNeverVerified() {
        FactoryCertificate saved = FactoryServiceImpl.cleanCertificates(List.of(custom().verified(true).build())).get(0);

        assertThat(saved.getTitle()).isEqualTo("ISO 13485:2016");
        assertThat(saved.isCustom()).isTrue();
        assertThat(saved.isVerified()).isFalse();
        assertThat(saved.getIssuer()).isNull();
        assertThat(saved.getImageUrl()).isEqualTo(DOC);
        assertThat(saved.getDocumentName()).isEqualTo("iso-13485.pdf");
        assertThat(saved.getId()).startsWith("cert-");
    }

    @Test
    void customCertificateAcceptsAbsoluteUploadUrlAndImageScans() {
        String url = "https://api.seekfactory.com/api/v1/media/0b5e7d1c-2f4a-4c1e-9a3b-7d6e5f4a3b2c.jpg";
        assertThat(FactoryServiceImpl.cleanCertificates(List.of(custom().imageUrl(url).build())).get(0).getImageUrl())
                .isEqualTo(url);
    }

    @Test
    void customCertificateRejectsExternalOrUnsupportedDocuments() {
        for (String url : List.of("https://evil.example/cert.pdf",
                "/api/v1/media/0b5e7d1c-2f4a-4c1e-9a3b-7d6e5f4a3b2c.zip",
                "/api/v1/media/../secret.pdf", "")) {
            assertThatThrownBy(() -> FactoryServiceImpl.cleanCertificates(List.of(custom().imageUrl(url).build())))
                    .as(url).isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void customCertificateRequiresAName() {
        assertThatThrownBy(() -> FactoryServiceImpl.cleanCertificates(List.of(custom().title(" ").build())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("certificate name");
    }

    @Test
    void presetCertificateStillRequiresIssuer() {
        FactoryCertificate preset = FactoryCertificate.builder().title("ISO 9001").imageUrl("/api/v1/media/x.png").build();
        assertThatThrownBy(() -> FactoryServiceImpl.cleanCertificates(List.of(preset)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    void existingPresetCertificatesStillWork() {
        FactoryCertificate preset = FactoryCertificate.builder().id("cert-1").title("ISO 9001").issuer("TUV")
                .imageUrl("https://cdn.example.com/iso.jpg").category("Quality").build();
        FactoryCertificate saved = FactoryServiceImpl.cleanCertificates(List.of(preset)).get(0);
        assertThat(saved.isCustom()).isFalse();
        assertThat(saved.getIssuer()).isEqualTo("TUV");
        assertThat(saved.getId()).isEqualTo("cert-1");
    }
}
