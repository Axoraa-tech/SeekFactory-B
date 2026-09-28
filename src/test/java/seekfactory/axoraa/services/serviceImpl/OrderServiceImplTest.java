package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import seekfactory.axoraa.dto.Request.order.OrderCreateRequest;
import seekfactory.axoraa.dto.Request.order.OrderStatusUpdateRequest;
import seekfactory.axoraa.dto.Response.order.OrderResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Notification;
import seekfactory.axoraa.entity.OrderRequest;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.NotificationType;
import seekfactory.axoraa.enums.OrderStatus;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ForbiddenException;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.NotificationRepository;
import seekfactory.axoraa.repository.OrderRequestRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.repository.UserRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderRequestRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private ManufacturerRepository manufacturerRepository;
    @Mock private NotificationRepository notificationRepository;

    @InjectMocks private OrderServiceImpl service;

    private User buyer;
    private User supplierUser;
    private Manufacturer factory;
    private Product product;

    @BeforeEach
    void setUp() {
        buyer = User.builder().name("Arjun").companyName("Apex Components").email("arjun@x.com").phone("+91 1").country("India").build();
        buyer.setId("u-buyer");
        supplierUser = User.builder().name("Chen").build();
        supplierUser.setId("u-supplier");
        factory = Manufacturer.builder().name("Dongguan Mould").slug("dongguan").user(supplierUser).build();
        factory.setId("mfr-1");
        product = Product.builder().name("HPDC Housing").slug("hpdc").manufacturer(factory)
                .priceInr(new BigDecimal("1500.00")).unit("Piece").isActive(true).build();
        product.setId("p-1");

        lenient().when(userRepository.findById("u-buyer")).thenReturn(Optional.of(buyer));
        lenient().when(productRepository.findBySlug("hpdc")).thenReturn(Optional.of(product));
        lenient().when(orderRepository.save(any(OrderRequest.class))).thenAnswer(inv -> {
            OrderRequest o = inv.getArgument(0);
            if (o.getId() == null) o.setId("o-1");
            if (o.getCreatedAt() == null) o.setCreatedAt(Instant.now());
            return o;
        });
    }

    @Test
    void placeOrderSnapshotsProductAndNotifiesSeller() {
        OrderResponse res = service.placeOrder("u-buyer", new OrderCreateRequest("hpdc", 40, "  Need ADC12 alloy  "));

        assertThat(res.getReferenceNumber()).matches("ORD-\\d{4}-[A-Z2-9]{6}");
        assertThat(res.getStatus()).isEqualTo("PENDING");
        assertThat(res.getProductName()).isEqualTo("HPDC Housing");
        assertThat(res.getEstimatedTotalInr()).isEqualByComparingTo("60000");
        assertThat(res.getBuyerNote()).isEqualTo("Need ADC12 alloy");
        // Buyer's own view does not echo their contact details
        assertThat(res.getBuyer().getEmail()).isNull();

        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(sent.capture());
        assertThat(sent.getValue().getUser()).isSameAs(supplierUser);
        assertThat(sent.getValue().getNotificationType()).isEqualTo(NotificationType.ORDER);
        assertThat(sent.getValue().getBody()).contains("Apex Components").contains("40");
    }

    @Test
    void factoryCannotOrderItsOwnProduct() {
        when(userRepository.findById("u-supplier")).thenReturn(Optional.of(supplierUser));
        assertThatThrownBy(() -> service.placeOrder("u-supplier", new OrderCreateRequest("hpdc", 1, null)))
                .isInstanceOf(BadRequestException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void doubleClickReturnsExistingPendingOrder() {
        OrderRequest existing = OrderRequest.builder().referenceNumber("ORD-2026-AAAAAA").product(product)
                .manufacturer(factory).buyer(buyer).productName("HPDC Housing").quantity(40).build();
        existing.setId("o-existing");
        when(orderRepository.findFirstByBuyerIdAndProductIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                eq("u-buyer"), eq("p-1"), eq(OrderStatus.PENDING), any())).thenReturn(Optional.of(existing));

        OrderResponse res = service.placeOrder("u-buyer", new OrderCreateRequest("hpdc", 40, null));

        assertThat(res.getId()).isEqualTo("o-existing");
        verify(orderRepository, never()).save(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void sellerUpdatesStatusAndBuyerIsNotified() {
        OrderRequest order = OrderRequest.builder().referenceNumber("ORD-2026-BBBBBB").product(product)
                .manufacturer(factory).buyer(buyer).productName("HPDC Housing").quantity(40).build();
        order.setId("o-1");
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(order));

        OrderResponse res = service.updateStatus("u-supplier", "o-1", new OrderStatusUpdateRequest("confirmed", "PO received"));

        assertThat(res.getStatus()).isEqualTo("CONFIRMED");
        assertThat(res.getSellerNote()).isEqualTo("PO received");
        assertThat(res.getBuyer().getEmail()).isEqualTo("arjun@x.com");   // seller sees buyer contact
        ArgumentCaptor<Notification> sent = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(sent.capture());
        assertThat(sent.getValue().getUser()).isSameAs(buyer);
        assertThat(sent.getValue().getTitle()).contains("Confirmed");
    }

    @Test
    void anotherFactoryCannotUpdateTheOrder() {
        OrderRequest order = OrderRequest.builder().manufacturer(factory).buyer(buyer).productName("x").quantity(1).build();
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.updateStatus("u-other", "o-1", new OrderStatusUpdateRequest("CONTACTED", null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void unknownStatusIsRejected() {
        assertThatThrownBy(() -> service.updateStatus("u-supplier", "o-1", new OrderStatusUpdateRequest("SHIPPED_BY_DRONE", null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listFactoryOrdersIsEmptyForUserWithoutFactory() {
        when(manufacturerRepository.findByUserId(anyString())).thenReturn(Optional.empty());
        assertThat(service.listFactoryOrders("u-nobody")).isEqualTo(List.of());
    }
}
