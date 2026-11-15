package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import seekfactory.axoraa.dto.Request.plan.ProductLimitDecisionRequest;
import seekfactory.axoraa.dto.Request.plan.ProductLimitUpgradeRequest;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.ProductLimitRequest;
import seekfactory.axoraa.enums.ProductLimitRequestStatus;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ForbiddenException;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.ProductLimitRequestRepository;
import seekfactory.axoraa.repository.ProductRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductLimitServiceTest {
    @Mock ManufacturerRepository manufacturerRepository;
    @Mock ProductRepository productRepository;
    @Mock ProductLimitRequestRepository requestRepository;
    @InjectMocks ProductLimitService service;
    private Manufacturer factory;

    @BeforeEach
    void setup() {
        factory = Manufacturer.builder().name("Factory").build();
        factory.setId("f1");
        lenient().when(manufacturerRepository.findByIdForUpdate("f1")).thenReturn(Optional.of(factory));
        lenient().when(requestRepository.save(any(ProductLimitRequest.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test void newFactoryDefaultsToThreeProducts() {
        assertThat(ProductLimitService.limitOf(factory)).isEqualTo(3);
    }

    @Test void allowsProductsBelowTheLimit() {
        when(productRepository.countByManufacturerIdAndIsActiveTrue("f1")).thenReturn(2L);
        assertThatCode(() -> service.requireRoomForProduct(factory)).doesNotThrowAnyException();
    }

    @Test void blocksTheFourthProductWithAClearMessage() {
        when(productRepository.countByManufacturerIdAndIsActiveTrue("f1")).thenReturn(3L);
        assertThatThrownBy(() -> service.requireRoomForProduct(factory))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("allows 3 products")
                .hasMessageContaining("contact admin");
    }

    @Test void grandfatheredFactoryAboveLimitKeepsProductsButCannotAdd() {
        factory.setProductLimit(3);
        when(productRepository.countByManufacturerIdAndIsActiveTrue("f1")).thenReturn(7L);
        assertThatThrownBy(() -> service.requireRoomForProduct(factory)).isInstanceOf(ForbiddenException.class);
        assertThat(service.usage(factory).productCount()).isEqualTo(7);
        assertThat(service.usage(factory).canAddProduct()).isFalse();
        verify(productRepository, never()).save(any());
    }

    @Test void upgradeRequestRecordsUsageAndStaysPending() {
        when(productRepository.countByManufacturerIdAndIsActiveTrue("f1")).thenReturn(3L);
        var response = service.requestUpgrade(factory, new ProductLimitUpgradeRequest(10, " Need more SKUs "));
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.currentCount()).isEqualTo(3);
        assertThat(response.currentLimit()).isEqualTo(3);
        assertThat(response.requestedLimit()).isEqualTo(10);
        assertThat(response.message()).isEqualTo("Need more SKUs");
        assertThat(factory.getProductLimit()).isEqualTo(3); // nothing changes until an admin approves
    }

    @Test void upgradeRequestMustExceedCurrentLimit() {
        assertThatThrownBy(() -> service.requestUpgrade(factory, new ProductLimitUpgradeRequest(3, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test void onlyOnePendingRequestAtATime() {
        when(requestRepository.existsByManufacturerIdAndStatus("f1", ProductLimitRequestStatus.PENDING)).thenReturn(true);
        assertThatThrownBy(() -> service.requestUpgrade(factory, new ProductLimitUpgradeRequest(5, null)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("pending");
    }

    private ProductLimitRequest pendingRequest() {
        ProductLimitRequest r = ProductLimitRequest.builder().manufacturer(factory)
                .currentCount(3).currentLimit(3).requestedLimit(10).build();
        r.setId("r1");
        when(requestRepository.findByIdForUpdate("r1")).thenReturn(Optional.of(r));
        return r;
    }

    @Test void approvingRaisesLimitToRequested() {
        ProductLimitRequest r = pendingRequest();
        service.approve("r1", null, "admin");
        assertThat(factory.getProductLimit()).isEqualTo(10);
        assertThat(r.getStatus()).isEqualTo(ProductLimitRequestStatus.APPROVED);
        assertThat(r.getReviewedBy()).isEqualTo("admin");
        assertThat(r.getReviewedAt()).isNotNull();
    }

    @Test void adminMayApproveADifferentLimit() {
        pendingRequest();
        service.approve("r1", new ProductLimitDecisionRequest(5, "Starting with 5"), "admin");
        assertThat(factory.getProductLimit()).isEqualTo(5);
    }

    @Test void rejectingNeedsAReasonAndKeepsLimit() {
        assertThatThrownBy(() -> service.reject("r1", new ProductLimitDecisionRequest(null, " "), "admin"))
                .isInstanceOf(BadRequestException.class);
        ProductLimitRequest r = pendingRequest();
        service.reject("r1", new ProductLimitDecisionRequest(null, "Please verify the factory first"), "admin");
        assertThat(r.getStatus()).isEqualTo(ProductLimitRequestStatus.REJECTED);
        assertThat(r.getAdminNote()).isEqualTo("Please verify the factory first");
        assertThat(factory.getProductLimit()).isEqualTo(3);
    }

    @Test void handledRequestCannotBeHandledAgain() {
        ProductLimitRequest r = pendingRequest();
        r.setStatus(ProductLimitRequestStatus.APPROVED);
        assertThatThrownBy(() -> service.approve("r1", null, "admin")).isInstanceOf(BadRequestException.class);
    }
}
