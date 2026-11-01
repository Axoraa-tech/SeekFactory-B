package seekfactory.axoraa.services.serviceImpl;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class R2PrivateDeliveryTest {
    @Test void rejectsPublicDeliveryOfMixedPrivacyR2Bucket() {
        assertThatThrownBy(() -> S3MediaStorageService.safePublicBaseUrl(
                "https://synthetic.r2.cloudflarestorage.com","https://media.example.invalid"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("private chat attachments");
    }
    @Test void permitsPrivateR2SignedLinks() {
        assertThat(S3MediaStorageService.safePublicBaseUrl("https://synthetic.r2.cloudflarestorage.com", "")).isEmpty();
    }
    @Test void preservesOtherProviderPrefixRestrictedCdnConfiguration() {
        assertThat(S3MediaStorageService.safePublicBaseUrl("https://objects.example.invalid", "https://cdn.example.invalid"))
                .isEqualTo("https://cdn.example.invalid");
    }
}
