package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import seekfactory.axoraa.dto.Request.manufacturer.ManufacturerUpdateRequest;
import seekfactory.axoraa.dto.Request.manufacturer.VerificationSubmitRequest;
import seekfactory.axoraa.dto.Request.product.ProductUpdateRequest;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;
import seekfactory.axoraa.dto.common.FactoryCertificate;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.enums.FeedTab;
import seekfactory.axoraa.enums.VerificationStatus;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ForbiddenException;
import seekfactory.axoraa.repository.CategoryRepository;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.repository.Reels.ReelRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Seller catalogue editing, pausing, profile fields and verification submission. */
@ExtendWith(MockitoExtension.class)
class FactoryServiceImplCatalogTest {

    private static final String USER_ID = "usr-supp";

    @Mock private ManufacturerRepository manufacturerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ReelRepository reelRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ModelMapper modelMapper;

    @InjectMocks private FactoryServiceImpl service;

    private Manufacturer manufacturer;
    private Product product;

    @BeforeEach
    void setUp() {
        manufacturer = Manufacturer.builder().name("Mine").verificationStatus(VerificationStatus.PENDING).build();
        manufacturer.setId("mfr-1");
        Category category = Category.builder().name("CNC").build();
        category.setId("cat-1");
        product = Product.builder().manufacturer(manufacturer).category(category).name("Lathe")
                .imageUrl("/api/v1/media/a.png").isActive(true).listed(true).build();
        product.setId("p-1");

        when(manufacturerRepository.findByUserId(USER_ID)).thenReturn(Optional.of(manufacturer));
        lenient().when(productRepository.findById("p-1")).thenReturn(Optional.of(product));
        lenient().when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(reelRepository.save(any(Reel.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(manufacturerRepository.save(any(Manufacturer.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(modelMapper.map(any(Product.class), any())).thenReturn(new ProductResponse());
        lenient().when(modelMapper.map(any(Manufacturer.class), any())).thenReturn(new ManufacturerResponse());
    }

    @Test
    void editReplacesGalleryAndCoverAndKeepsOmittedFields() {
        ProductUpdateRequest request = ProductUpdateRequest.builder()
                .imageUrls(List.of("/api/v1/media/b.png", "/api/v1/media/c.png", "/api/v1/media/b.png"))
                .build();

        ProductResponse response = service.updateProduct(USER_ID, "p-1", request);

        assertThat(product.getImageUrl()).isEqualTo("/api/v1/media/b.png");
        assertThat(product.getImageUrls()).containsExactly("/api/v1/media/b.png", "/api/v1/media/c.png");
        assertThat(product.getName()).isEqualTo("Lathe");
        assertThat(response.getImageUrls()).containsExactly("/api/v1/media/b.png", "/api/v1/media/c.png");
    }

    @Test
    void editRejectsUnsafeImageUrls() {
        ProductUpdateRequest request = ProductUpdateRequest.builder()
                .imageUrls(List.of("javascript:alert(1)")).build();

        assertThatThrownBy(() -> service.updateProduct(USER_ID, "p-1", request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void datasheetMustBePdfAndBlankRemovesIt() {
        assertThatThrownBy(() -> service.updateProduct(USER_ID, "p-1",
                ProductUpdateRequest.builder().datasheetUrl("/api/v1/media/x.png").build()))
                .isInstanceOf(BadRequestException.class);

        service.updateProduct(USER_ID, "p-1", ProductUpdateRequest.builder()
                .datasheetUrl("/api/v1/media/x.pdf").datasheetName("spec/../sheet.pdf").build());
        assertThat(product.getDatasheetUrl()).isEqualTo("/api/v1/media/x.pdf");
        assertThat(product.getDatasheetName()).doesNotContain("/");

        service.updateProduct(USER_ID, "p-1", ProductUpdateRequest.builder().datasheetUrl("").build());
        assertThat(product.getDatasheetUrl()).isNull();
    }

    @Test
    void pauseHidesFromBuyersWithoutDeleting() {
        ProductResponse response = service.setProductListed(USER_ID, "p-1", false);

        assertThat(product.getListed()).isFalse();
        assertThat(product.getIsActive()).isTrue();
        assertThat(product.isPubliclyVisible()).isFalse();
        assertThat(response.getListed()).isFalse();
    }

    @Test
    void cannotEditAnotherFactorysProduct() {
        Manufacturer other = Manufacturer.builder().name("Other").build();
        other.setId("mfr-2");
        product.setManufacturer(other);

        assertThatThrownBy(() -> service.setProductListed(USER_ID, "p-1", false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void pauseSeek() {
        Reel reel = Reel.builder().manufacturer(manufacturer).title("Tour").posterUrl("/p.png")
                .feedTab(FeedTab.FOR_YOU).build();
        reel.setId("r-1");
        when(reelRepository.findById("r-1")).thenReturn(Optional.of(reel));

        assertThat(service.setSeekListed(USER_ID, "r-1", false).getListed()).isFalse();
        assertThat(reel.getListed()).isFalse();
    }

    @Test
    void profileSavesWebsiteTurnoverLinesYearAndCertifications() {
        ManufacturerUpdateRequest request = ManufacturerUpdateRequest.builder()
                .websiteUrl("www.acme-forge.com")
                .annualTurnover("USD 5-10 Million")
                .productionLines(6)
                .yearsEstablished(2004)
                .certifications(List.of("ISO 9001", " CE ", "ISO 9001", ""))
                .build();

        ManufacturerResponse response = service.updateProfile(USER_ID, request);

        assertThat(manufacturer.getWebsiteUrl()).isEqualTo("https://www.acme-forge.com");
        assertThat(manufacturer.getAnnualTurnover()).isEqualTo("USD 5-10 Million");
        assertThat(manufacturer.getProductionLines()).isEqualTo(6);
        assertThat(manufacturer.getYearsEstablished()).isEqualTo(2004);
        assertThat(manufacturer.getCertifications()).containsExactly("ISO 9001", "CE");
        assertThat(response.getCertifications()).containsExactly("ISO 9001", "CE");
    }

    @Test
    void certificatesAreStoredUnverifiedWithIds() {
        ManufacturerUpdateRequest request = ManufacturerUpdateRequest.builder()
                .certificates(List.of(FactoryCertificate.builder().title(" ISO 9001 ").issuer("TUV")
                        .imageUrl("/api/v1/media/c.png").verified(true).certNumber(" ").build()))
                .build();

        service.updateProfile(USER_ID, request);

        assertThat(manufacturer.getCertificates()).hasSize(1);
        FactoryCertificate saved = manufacturer.getCertificates().get(0);
        assertThat(saved.isVerified()).isFalse();
        assertThat(saved.getId()).startsWith("cert-");
        assertThat(saved.getTitle()).isEqualTo("ISO 9001");
        assertThat(saved.getCertNumber()).isNull();

        ManufacturerUpdateRequest unsafe = ManufacturerUpdateRequest.builder()
                .certificates(List.of(FactoryCertificate.builder().title("X").issuer("Y")
                        .imageUrl("data:image/png;base64,AAA").build()))
                .build();
        assertThatThrownBy(() -> service.updateProfile(USER_ID, unsafe)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void profileRejectsNonHttpWebsite() {
        ManufacturerUpdateRequest request = ManufacturerUpdateRequest.builder()
                .websiteUrl("javascript:alert(1)").build();

        assertThatThrownBy(() -> service.updateProfile(USER_ID, request)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void verificationSubmissionQueuesForReviewAndClearsRejection() {
        manufacturer.setVerificationStatus(VerificationStatus.REJECTED);
        manufacturer.setRejectionReason("Blurry licence");

        var response = service.submitVerification(USER_ID, VerificationSubmitRequest.builder()
                .companyRegNumber(" 91440300MA5 ").factoryAddress("Dongguan, Guangdong")
                .certifications(List.of("ISO 9001")).build());

        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.isSubmitted()).isTrue();
        assertThat(manufacturer.getCompanyRegNumber()).isEqualTo("91440300MA5");
        assertThat(manufacturer.getRejectionReason()).isNull();
        assertThat(manufacturer.getSubmittedAt()).isNotNull();
    }

    @Test
    void approvedFactoryCannotResubmit() {
        manufacturer.setVerificationStatus(VerificationStatus.APPROVED);

        assertThatThrownBy(() -> service.submitVerification(USER_ID, VerificationSubmitRequest.builder()
                .companyRegNumber("X").factoryAddress("Y").build()))
                .isInstanceOf(BadRequestException.class);
    }
}
