package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.ManufacturerDetail;
import seekfactory.axoraa.dto.common.FactoryCertificate;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.repository.Rfqs.RfqRepository;
import seekfactory.axoraa.repository.SubscriptionPlanRepository;
import seekfactory.axoraa.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The admin review of a manufacturer shows the certificate documents the factory uploaded. */
class AdminManufacturerCertificatesTest {

    @Test
    void reviewDetailIncludesUploadedCertificatesAndMembershipPlan() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        ManufacturerRepository manufacturers = mock(ManufacturerRepository.class);
        FactoryCertificate iso = FactoryCertificate.builder()
                .id("c1").title("ISO 9001:2015").issuer("SGS").certNumber("CN-QMS-0042")
                .issueDate("2024-03-01").expiryDate("2027-02-28")
                .imageUrl("/api/v1/media/11111111-2222-3333-4444-555555555555.pdf").documentName("iso9001.pdf")
                .build();
        Manufacturer factory = Manufacturer.builder().name("Laser Works").membershipPlan("VIP").build();
        factory.setId("m1");
        factory.setCertificates(List.of(iso));
        when(manufacturers.findById("m1")).thenReturn(Optional.of(factory));

        AdminManagementServiceImpl service = new AdminManagementServiceImpl(jdbc, mock(UserRepository.class), manufacturers,
                mock(RfqRepository.class), mock(SubscriptionPlanRepository.class), mock(ReelRepository.class),
                mock(ProductRepository.class), mock(ReelRemover.class));

        ManufacturerDetail detail = service.getManufacturer("m1");

        assertThat(detail.certificateDocuments()).singleElement().satisfies(c -> {
            assertThat(c.getTitle()).isEqualTo("ISO 9001:2015");
            assertThat(c.getIssuer()).isEqualTo("SGS");
            assertThat(c.getCertNumber()).isEqualTo("CN-QMS-0042");
            assertThat(c.getExpiryDate()).isEqualTo("2027-02-28");
            assertThat(c.getImageUrl()).endsWith(".pdf");
        });
        assertThat(detail.membershipPlan()).isEqualTo("vip");
        assertThat(detail.planName()).isEqualTo("VIP Global Partner");
    }

    @Test
    void aFactoryWithoutCertificatesGetsAnEmptyList() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        ManufacturerRepository manufacturers = mock(ManufacturerRepository.class);
        Manufacturer factory = Manufacturer.builder().name("New Factory").build();
        factory.setId("m2");
        factory.setCertificates(null);
        when(manufacturers.findById("m2")).thenReturn(Optional.of(factory));
        AdminManagementServiceImpl service = new AdminManagementServiceImpl(jdbc, mock(UserRepository.class), manufacturers,
                mock(RfqRepository.class), mock(SubscriptionPlanRepository.class), mock(ReelRepository.class),
                mock(ProductRepository.class), mock(ReelRemover.class));

        ManufacturerDetail detail = service.getManufacturer("m2");

        assertThat(detail.certificateDocuments()).isEmpty();
        assertThat(detail.membershipPlan()).isEqualTo("basic");
    }
}
