package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import seekfactory.axoraa.dto.Request.product.ProductCreateRequest;
import seekfactory.axoraa.dto.Request.product.ProductUpdateRequest;
import seekfactory.axoraa.dto.Response.product.ProductResponse;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ForbiddenException;
import seekfactory.axoraa.repository.CategoryRepository;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductVideoTest {
    private static final String VIDEO = "/api/v1/media/0b5e7d1c-2f4a-4c1e-9a3b-7d6e5f4a3b2c.mp4";
    @Mock ManufacturerRepository manufacturerRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ProductRepository productRepository;
    @Mock ReelRepository reelRepository;
    @Mock ModelMapper modelMapper;
    @InjectMocks FactoryServiceImpl service;
    private Product product;
    private Manufacturer manufacturer;
    private Category category;

    @BeforeEach
    void setup() {
        manufacturer = Manufacturer.builder().name("Factory").build();
        manufacturer.setId("factory");
        category = Category.builder().name("Tools").build();
        category.setId("tools");
        product = Product.builder().manufacturer(manufacturer).category(category).name("Drill")
                .imageUrl("/api/v1/media/photo.jpg").videoUrl(VIDEO).build();
        product.setId("product");
        lenient().when(manufacturerRepository.findByUserId("seller")).thenReturn(Optional.of(manufacturer));
        lenient().when(categoryRepository.findById("tools")).thenReturn(Optional.of(category));
        lenient().when(productRepository.findById("product")).thenReturn(Optional.of(product));
        lenient().when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(modelMapper.map(any(Product.class), any())).thenReturn(new ProductResponse());
    }

    private ProductCreateRequest.ProductCreateRequestBuilder create() {
        return ProductCreateRequest.builder().name("Drill").categoryId("tools")
                .imageUrl("/api/v1/media/photo.jpg").priceInr(BigDecimal.TEN);
    }

    @Test void createReturnsVideoWithoutCreatingSeek() {
        assertThat(service.addProduct("seller", create().videoUrl(VIDEO).build()).getVideoUrl()).isEqualTo(VIDEO);
        verify(productRepository).save(argThat(p -> VIDEO.equals(p.getVideoUrl())));
        verifyNoInteractions(reelRepository);
    }

    @Test void legacyCreateHasNoVideo() {
        assertThat(service.addProduct("seller", create().build()).getVideoUrl()).isNull();
    }

    @Test void createRejectsInvalidVideo() {
        assertThatThrownBy(() -> service.addProduct("seller", create().videoUrl("javascript:alert(1)").build()))
                .isInstanceOf(BadRequestException.class);
        verify(productRepository, never()).save(any());
        verifyNoInteractions(reelRepository);
    }

    @Test void encodedUploadKeysAreCanonicalized() {
        String encoded = VIDEO.replace("/0b", "/%30b");
        assertThat(FactoryServiceImpl.productVideoUrl(encoded)).isEqualTo(VIDEO);
        assertThat(FactoryServiceImpl.productVideoUrl("https://api.example.com" + encoded + "?v=2"))
                .isEqualTo("https://api.example.com" + VIDEO + "?v=2");
        assertThat(FactoryServiceImpl.productVideoUrl("https://cdn.example.com/media/" + encoded.substring(encoded.lastIndexOf('/') + 1)))
                .isEqualTo("https://cdn.example.com/media/" + VIDEO.substring(VIDEO.lastIndexOf('/') + 1));
    }

    @Test void sellerReadIncludesVideo() {
        when(productRepository.findByManufacturerIdAndIsActiveTrue("factory")).thenReturn(List.of(product));
        assertThat(service.getProducts("seller")).extracting(ProductResponse::getVideoUrl).containsExactly(VIDEO);
    }

    @Test void updateReplacesVideoOnly() {
        String replacement = "https://cdn.example.com/new.webm?version=2";
        assertThat(service.updateProduct("seller", "product", ProductUpdateRequest.builder().videoUrl(replacement).build())
                .getVideoUrl()).isEqualTo(replacement);
        assertThat(product.getImageUrl()).isEqualTo("/api/v1/media/photo.jpg");
        verifyNoInteractions(reelRepository);
    }

    @Test void emptyStringClearsVideo() {
        assertThat(service.updateProduct("seller", "product", ProductUpdateRequest.builder().videoUrl("").build())
                .getVideoUrl()).isNull();
        assertThat(product.getVideoUrl()).isNull();
        verifyNoInteractions(reelRepository);
    }

    @Test void omittedAndNullJsonPreserveWhileBlankClears() {
        JsonMapper json = JsonMapper.builder().build();
        for (String payload : List.of("{}", "{\"name\":\"New drill\"}", "{\"videoUrl\":null}")) {
            service.updateProduct("seller", "product", json.readValue(payload, ProductUpdateRequest.class));
            assertThat(product.getVideoUrl()).as(payload).isEqualTo(VIDEO);
        }
        service.updateProduct("seller", "product", json.readValue("{\"videoUrl\":\"\"}", ProductUpdateRequest.class));
        assertThat(product.getVideoUrl()).isNull();
    }

    @Test void invalidUpdateKeepsExistingVideo() {
        assertThatThrownBy(() -> service.updateProduct("seller", "product",
                ProductUpdateRequest.builder().videoUrl("data:video/mp4;base64,abc").build()))
                .isInstanceOf(BadRequestException.class);
        assertThat(product.getVideoUrl()).isEqualTo(VIDEO);
        verify(productRepository, never()).save(any());
    }

    @Test void anotherFactoryCannotReplaceVideo() {
        Manufacturer other = Manufacturer.builder().build();
        other.setId("other");
        product.setManufacturer(other);
        assertThatThrownBy(() -> service.updateProduct("seller", "product",
                ProductUpdateRequest.builder().videoUrl("").build())).isInstanceOf(ForbiddenException.class);
        assertThat(product.getVideoUrl()).isEqualTo(VIDEO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "data:video/mp4;base64,abc", "file:///tmp/test.mp4",
            "//evil.example/test.mp4", "/outside/test.mp4", "/api/v1/media/../test.mp4",
            "/api/v1/media/x.jpg", "https:///test.mp4", "https://user:password@example.com/test.mp4",
            "https://example.com/test.jpg", "https://example.com/a\nb.mp4", "https://example.com/a b.mp4"})
    void unsafeOrNonVideoUrlsRejected(String url) {
        final String input = url;
        assertThatThrownBy(() -> FactoryServiceImpl.productVideoUrl(input)).isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://cdn.example.com/demo.mp4", "http://localhost:8080/demo.mov",
            "https://cdn.example.com/demo.WEBM?token=value", VIDEO})
    void supportedVideoUrlsAccepted(String url) {
        assertThat(FactoryServiceImpl.productVideoUrl(" " + url + " ")).isEqualTo(url);
    }
}
