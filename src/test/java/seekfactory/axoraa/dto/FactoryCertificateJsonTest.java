package seekfactory.axoraa.dto;

import org.junit.jupiter.api.Test;
import seekfactory.axoraa.dto.common.FactoryCertificate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A standard (preset) certificate is sent without "custom" (and older clients may omit "verified"):
 * the request must still be readable, with both treated as false.
 */
class FactoryCertificateJsonTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void readsACertificateWithoutTheOptionalFlags() {
        FactoryCertificate cert = json.readValue("""
                {"id":"c1","title":"ISO 9001:2015","issuer":"SGS","certNumber":"CN-QMS-0042",
                 "issueDate":"2024-03-01","expiryDate":"2027-02-28","imageUrl":"/api/v1/media/a.png"}""",
                FactoryCertificate.class);
        assertThat(cert.getTitle()).isEqualTo("ISO 9001:2015");
        assertThat(cert.isCustom()).isFalse();
        assertThat(cert.isVerified()).isFalse();
    }

    @Test
    void readsExplicitNullsAsFalse() {
        FactoryCertificate cert = json.readValue("""
                {"title":"ISO 9001","imageUrl":"/api/v1/media/a.png","custom":null,"verified":null}""",
                FactoryCertificate.class);
        assertThat(cert.isCustom()).isFalse();
        assertThat(cert.isVerified()).isFalse();
    }

    @Test
    void keepsACustomCertificateCustom() {
        FactoryCertificate cert = json.readValue("""
                {"title":"ISO 13485:2016","imageUrl":"/api/v1/media/a.pdf","custom":true,"documentName":"iso.pdf"}""",
                FactoryCertificate.class);
        assertThat(cert.isCustom()).isTrue();
    }
}
