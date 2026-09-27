package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import seekfactory.axoraa.dto.Request.order.OrderContactRequest;
import seekfactory.axoraa.dto.Request.order.OrderCreateRequest;
import seekfactory.axoraa.dto.Request.order.OrderStatusUpdateRequest;
import seekfactory.axoraa.dto.Response.order.OrderResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.OrderRequest;
import seekfactory.axoraa.entity.Orders.CartItem;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.entity.Rfqs.Rfq;
import seekfactory.axoraa.entity.Rfqs.RfqQuote;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.Currency;
import seekfactory.axoraa.enums.OrderSource;
import seekfactory.axoraa.enums.OrderStatus;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.mapper.CatalogMapper;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.NotificationRepository;
import seekfactory.axoraa.repository.OrderRequestRepository;
import seekfactory.axoraa.repository.Orders.CartItemRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.repository.UserRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Buyer-side order flows layered on order requests: MOQ, cart checkout, cancel, RFQ quotes. */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplBuyerTest {

    @Mock private OrderRequestRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private ManufacturerRepository manufacturerRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private CatalogMapper catalogMapper;
    @InjectMocks private OrderServiceImpl service;

    private User buyer;
    private User supplierUser;
    private Manufacturer factory;
    private Product product;

    @BeforeEach
    void setUp() {
        buyer = User.builder().name("Arjun").companyName("Apex Components").build();
        buyer.setId("u-buyer");
        supplierUser = User.builder().name("Chen").build();
        supplierUser.setId("u-supplier");
        factory = Manufacturer.builder().name("Dongguan Mould").slug("dongguan").user(supplierUser).build();
        factory.setId("mfr-1");
        product = Product.builder().name("HPDC Housing").slug("hpdc").manufacturer(factory)
                .priceInr(new BigDecimal("1500.00")).unit("Piece").moq("50 pcs").isActive(true).build();
        product.setId("p-1");

        lenient().when(userRepository.findById("u-buyer")).thenReturn(Optional.of(buyer));
        lenient().when(productRepository.findBySlug("hpdc")).thenReturn(Optional.of(product));
        lenient().when(orderRepository.save(any(OrderRequest.class))).thenAnswer(inv -> {
            OrderRequest o = inv.getArgument(0);
            if (o.getId() == null) o.setId("o-" + System.nanoTime());
            if (o.getCreatedAt() == null) o.setCreatedAt(Instant.now());
            return o;
        });
    }

    private static OrderContactRequest contact() {
        return new OrderContactRequest("Arjun", "+91 90000 00000", "Plot 1, Pune", null);
    }

    @Test
    void orderBelowMinimumQuantityIsRejected() {
        assertThatThrownBy(() -> service.placeOrder("u-buyer", new OrderCreateRequest("hpdc", 49, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Minimum order");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void unpricedProductCanStillBeRequested() {
        product.setPriceInr(null);
        OrderResponse res = service.placeOrder("u-buyer", new OrderCreateRequest("hpdc", 50, null));
        assertThat(res.getUnitPriceInr()).isNull();
        assertThat(res.getEstimatedTotalInr()).isNull();
        assertThat(res.getSource()).isEqualTo("DIRECT");
        assertThat(res.getCancellable()).isTrue();
    }

    @Test
    void checkoutCreatesOneRequestPerLineAndEmptiesTheCart() {
        Product second = Product.builder().name("Bracket").slug("bracket").manufacturer(factory)
                .priceInr(new BigDecimal("200")).unit("Piece").isActive(true).build();
        second.setId("p-2");
        List<CartItem> cart = List.of(
                CartItem.builder().user(buyer).product(product).quantity(60).build(),
                CartItem.builder().user(buyer).product(second).quantity(5).build());
        when(cartItemRepository.findByUserIdOrderByCreatedAtDesc("u-buyer")).thenReturn(cart);

        List<OrderResponse> placed = service.checkout("u-buyer", contact());

        assertThat(placed).hasSize(2).allSatisfy(o -> {
            assertThat(o.getSource()).isEqualTo("CART");
            assertThat(o.getContactPhone()).isEqualTo("+91 90000 00000");
        });
        verify(cartItemRepository).deleteAll(cart);
        verify(notificationRepository, times(2)).save(any());
    }

    @Test
    void checkoutWritesNothingWhenAnyLineIsInvalid() {
        List<CartItem> cart = List.of(CartItem.builder().user(buyer).product(product).quantity(10).build());
        when(cartItemRepository.findByUserIdOrderByCreatedAtDesc("u-buyer")).thenReturn(cart);

        assertThatThrownBy(() -> service.checkout("u-buyer", contact())).isInstanceOf(BadRequestException.class);
        verify(orderRepository, never()).save(any());
        verify(cartItemRepository, never()).deleteAll(any());
    }

    @Test
    void buyerCanCancelUntilConfirmed() {
        OrderRequest order = OrderRequest.builder().referenceNumber("ORD-1").manufacturer(factory).buyer(buyer)
                .productName("HPDC Housing").quantity(50).status(OrderStatus.NEGOTIATING).build();
        when(orderRepository.findByIdAndBuyerId("o-1", "u-buyer")).thenReturn(Optional.of(order));

        OrderResponse res = service.cancelByBuyer("u-buyer", "o-1", "Found a local supplier");
        assertThat(res.getStatus()).isEqualTo("CANCELLED");
        assertThat(res.getCancelReason()).isEqualTo("Found a local supplier");
        verify(notificationRepository).save(any());

        order.setStatus(OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> service.cancelByBuyer("u-buyer", "o-1", null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void closedOrdersCannotBeReopenedBySeller() {
        OrderRequest order = OrderRequest.builder().referenceNumber("ORD-1").manufacturer(factory).buyer(buyer)
                .productName("HPDC Housing").quantity(50).status(OrderStatus.COMPLETED).build();
        order.setId("o-1");
        when(orderRepository.findById("o-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateStatus("u-supplier", "o-1", new OrderStatusUpdateRequest("PENDING", null)))
                .isInstanceOf(BadRequestException.class);
        // The note can still be edited on a closed order
        OrderResponse res = service.updateStatus("u-supplier", "o-1", new OrderStatusUpdateRequest("COMPLETED", "Invoice sent"));
        assertThat(res.getSellerNote()).isEqualTo("Invoice sent");
    }

    @Test
    void acceptedQuoteBecomesOneOrderRequestWithQuotedTotal() {
        Rfq rfq = Rfq.builder().user(buyer).productName("Die-cast housings").quantity("500 pcs").unit("Pieces").build();
        rfq.setId("rfq-1");
        RfqQuote quote = RfqQuote.builder().rfq(rfq).manufacturer(factory)
                .quotePrice(new BigDecimal("250000")).currency(Currency.INR).build();
        quote.setId("q-1");

        OrderResponse res = service.createFromQuote(quote, contact());

        assertThat(res.getSource()).isEqualTo(OrderSource.RFQ_QUOTE.name());
        assertThat(res.getRfqId()).isEqualTo("rfq-1");
        assertThat(res.getQuantity()).isEqualTo(500);
        assertThat(res.getQuotedTotal()).isEqualByComparingTo("250000");
        assertThat(res.getEstimatedTotalInr()).isEqualByComparingTo("250000");
        assertThat(res.getUnitPriceInr()).isEqualByComparingTo("500");

        when(orderRepository.existsByRfqQuoteId("q-1")).thenReturn(true);
        assertThatThrownBy(() -> service.createFromQuote(quote, contact())).isInstanceOf(BadRequestException.class);
    }

    @Test
    void foreignCurrencyQuoteHasNoInrUnitPrice() {
        Rfq rfq = Rfq.builder().user(buyer).productName("Moulds").quantity("2").unit("Sets").build();
        rfq.setId("rfq-2");
        RfqQuote quote = RfqQuote.builder().rfq(rfq).manufacturer(factory)
                .quotePrice(new BigDecimal("30000")).currency(Currency.USD).build();
        quote.setId("q-2");

        OrderResponse res = service.createFromQuote(quote, contact());
        assertThat(res.getCurrency()).isEqualTo("USD");
        assertThat(res.getUnitPriceInr()).isNull();
        assertThat(res.getEstimatedTotalInr()).isNull();
    }
}
