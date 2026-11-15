package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import seekfactory.axoraa.dto.Request.manufacturer.ManufacturerUpdateRequest;
import seekfactory.axoraa.dto.Request.product.ProductCreateRequest;
import seekfactory.axoraa.dto.Request.product.ProductUpdateRequest;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ForbiddenException;
import seekfactory.axoraa.repository.CategoryRepository;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.ProductRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductMediaLimitsTest {
    private static final String VIDEO = "/api/v1/media/0b5e7d1c-2f4a-4c1e-9a3b-7d6e5f4a3b2c.mp4";
    @Mock ManufacturerRepository manufacturerRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ProductRepository productRepository;
    @Mock ModelMapper modelMapper;
    @Mock ProductLimitService productLimitService;
    @InjectMocks FactoryServiceImpl service;
    private Manufacturer manufacturer;
    private Product product;

    private static List<String> photos(int n) {
        return IntStream.range(0, n).mapToObj(i -> "/api/v1/media/p" + i + ".jpg").toList();
    }

    @BeforeEach
    void setup() {
        manufacturer = Manufacturer.builder().name("Factory").build();
        manufacturer.setId("factory");
        Category category = Category.builder().name("Tools").build();
        category.setId("tools");
        product = Product.builder().manufacturer(manufacturer).category(category).name("Drill")
                .imageUrl(photos(1).get(0)).imageUrls(new ArrayList<>(photos(1))).isActive(true).build();
        product.setId("product");
        lenient().when(manufacturerRepository.findByUserId("seller")).thenReturn(Optional.of(manufacturer));
        lenient().when(manufacturerRepository.save(any(Manufacturer.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(categoryRepository.findById("tools")).thenReturn(Optional.of(category));
        lenient().when(productRepository.findById("product")).thenReturn(Optional.of(product));
        lenient().when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(modelMapper.map(any(Product.class), any())).thenReturn(new ProductResponse());
        lenient().when(modelMapper.map(any(Manufacturer.class), any())).thenReturn(new ManufacturerResponse());
    }

    private ProductCreateRequest.ProductCreateRequestBuilder create() {
        return ProductCreateRequest.builder().name("Drill").categoryId("tools")
                .imageUrl(photos(1).get(0)).priceInr(BigDecimal.TEN);
    }

    @Test void createAcceptsFourPhotosAndOneVideo() {
        ProductResponse res = service.addProduct("seller", create().imageUrls(photos(4)).videoUrl(VIDEO).build());
        assertThat(res.getImageUrls()).hasSize(4);
        assertThat(res.getVideoUrl()).isEqualTo(VIDEO);
        verify(productLimitService).requireRoomForProduct(manufacturer);
    }

    @Test void createRejectsFifthPhoto() {
        assertThatThrownBy(() -> service.addProduct("seller", create().imageUrls(photos(5)).build()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("at most 4 photos");
    }

    @Test void createIsBlockedWhenProductLimitReached() {
        doThrow(new ForbiddenException("Product limit reached")).when(productLimitService).requireRoomForProduct(manufacturer);
        assertThatThrownBy(() -> service.addProduct("seller", create().build())).isInstanceOf(ForbiddenException.class);
        verify(productRepository, never()).save(any());
    }

    @Test void editCannotGrowPastFourPhotos() {
        assertThatThrownBy(() -> service.updateProduct("seller", "product",
                ProductUpdateRequest.builder().imageUrls(photos(5)).build()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test void legacyProductWithEightPhotosCanStillBeEditedButNotGrow() {
        product.setImageUrls(new ArrayList<>(photos(8)));
        service.updateProduct("seller", "product", ProductUpdateRequest.builder().imageUrls(photos(8)).build());
        assertThat(product.getImageUrls()).hasSize(8);
        service.updateProduct("seller", "product", ProductUpdateRequest.builder().name("Drill v2").build());
        assertThat(product.getImageUrls()).hasSize(8); // an untouched gallery is preserved
    }

    @Test void profileVideoIsValidatedSavedAndCleared() {
        service.updateProfile("seller", ManufacturerUpdateRequest.builder().profileVideoUrl(VIDEO).build());
        assertThat(manufacturer.getProfileVideoUrl()).isEqualTo(VIDEO);

        service.updateProfile("seller", ManufacturerUpdateRequest.builder().name("Renamed").build());
        assertThat(manufacturer.getProfileVideoUrl()).isEqualTo(VIDEO); // omitted field keeps the video

        assertThatThrownBy(() -> service.updateProfile("seller",
                ManufacturerUpdateRequest.builder().profileVideoUrl("javascript:alert(1)").build()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.updateProfile("seller", ManufacturerUpdateRequest.builder()
                .profileVideoUrl("/api/v1/media/0b5e7d1c-2f4a-4c1e-9a3b-7d6e5f4a3b2c.png").build()))
                .isInstanceOf(BadRequestException.class);

        service.updateProfile("seller", ManufacturerUpdateRequest.builder().profileVideoUrl("").build());
        assertThat(manufacturer.getProfileVideoUrl()).isNull();
    }
}
