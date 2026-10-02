package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import seekfactory.axoraa.dto.Request.rfq.RfqCreateRequest;
import seekfactory.axoraa.dto.Response.rfq.RfqResponse;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.entity.Rfqs.Rfq;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.NotificationType;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.mapper.CatalogMapper;
import seekfactory.axoraa.repository.CategoryRepository;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.OrderRequestRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.repository.Rfqs.RfqQuoteRepository;
import seekfactory.axoraa.repository.Rfqs.RfqRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.NotificationService;
import seekfactory.axoraa.services.services.OrderService;

import java.time.Instant;
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

/** RFQs sent to one factory about one of its products (from its page, a seek or a product page). */
@ExtendWith(MockitoExtension.class)
class RfqServiceImplTargetTest {

    @Mock private RfqRepository rfqRepository;
    @Mock private RfqQuoteRepository rfqQuoteRepository;
    @Mock private OrderRequestRepository orderRequestRepository;
    @Mock private UserRepository userRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ManufacturerRepository manufacturerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ReelRepository reelRepository;
    @Mock private OrderService orderService;
    @Mock private NotificationService notificationService;
    @Mock private CatalogMapper catalogMapper;

    @InjectMocks private RfqServiceImpl service;

    private User buyer;
    private User supplierUser;
    private Manufacturer factory;
    private Category cnc;
    private Product spindle;

    @BeforeEach
    void setUp() {
        buyer = User.builder().name("Arjun").build();
        buyer.setId("u-buyer");
        supplierUser = User.builder().name("Chen").build();
        supplierUser.setId("u-supplier");
        factory = Manufacturer.builder().name("Dongguan Precision").slug("dongguan").user(supplierUser).verified(true).build();
        factory.setId("mfr-1");
        cnc = Category.builder().name("CNC Machining").build();
        cnc.setId("cat-cnc");
        spindle = Product.builder().name("5-axis spindle").slug("spindle").manufacturer(factory).category(cnc)
                .isActive(true).listed(true).build();
        spindle.setId("prod-1");

        lenient().when(userRepository.findById("u-buyer")).thenReturn(Optional.of(buyer));
        lenient().when(productRepository.findById("prod-1")).thenReturn(Optional.of(spindle));
        lenient().when(rfqRepository.save(any(Rfq.class))).thenAnswer(inv -> {
            Rfq rfq = inv.getArgument(0);
            rfq.setId("rfq-1");
            rfq.setCreatedAt(Instant.now());
            return rfq;
        });
    }

    private RfqCreateRequest request() {
        return RfqCreateRequest.builder()
                .productName("typed by the buyer")
                .quantity("50")
                .details("For an automotive line")
                .companyName("Arjun Motors")
                .categoryId("some-other-category")
                .build();
    }

    @Test
    void productDecidesFactoryNameAndCategoryAndNotifiesTheFactory() {
        RfqCreateRequest req = request();
        req.setProductId("prod-1");

        RfqResponse response = service.submit("u-buyer", req);

        ArgumentCaptor<Rfq> saved = ArgumentCaptor.forClass(Rfq.class);
        verify(rfqRepository).save(saved.capture());
        assertThat(saved.getValue().getManufacturer()).isSameAs(factory);
        assertThat(saved.getValue().getProduct()).isSameAs(spindle);
        assertThat(saved.getValue().getProductName()).isEqualTo("5-axis spindle");
        assertThat(saved.getValue().getCategory()).isSameAs(cnc);
        assertThat(response.getManufacturerId()).isEqualTo("mfr-1");
        assertThat(response.getProductSlug()).isEqualTo("spindle");
        verify(notificationService).notify(eq(supplierUser), eq(NotificationType.RFQ), anyString(), anyString(), eq("rfq-1"));
    }

    @Test
    void productOfAnotherFactoryIsRejected() {
        RfqCreateRequest req = request();
        req.setProductId("prod-1");
        req.setManufacturerId("mfr-other");

        assertThatThrownBy(() -> service.submit("u-buyer", req)).isInstanceOf(BadRequestException.class);
        verify(rfqRepository, never()).save(any());
    }

    @Test
    void hiddenProductIsRejected() {
        spindle.setListed(false);
        RfqCreateRequest req = request();
        req.setProductId("prod-1");

        assertThatThrownBy(() -> service.submit("u-buyer", req)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void seekMustBelongToTheFactory() {
        Manufacturer other = Manufacturer.builder().name("Other").verified(true).build();
        other.setId("mfr-2");
        Reel reel = Reel.builder().manufacturer(other).build();
        reel.setId("reel-1");
        when(reelRepository.findById("reel-1")).thenReturn(Optional.of(reel));
        RfqCreateRequest req = request();
        req.setProductId("prod-1");
        req.setReelId("reel-1");

        assertThatThrownBy(() -> service.submit("u-buyer", req)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void seekOfTheFactoryIsLinked() {
        Reel reel = Reel.builder().manufacturer(factory).build();
        reel.setId("reel-1");
        when(reelRepository.findById("reel-1")).thenReturn(Optional.of(reel));
        RfqCreateRequest req = request();
        req.setProductId("prod-1");
        req.setReelId("reel-1");

        assertThat(service.submit("u-buyer", req).getSourceReelId()).isEqualTo("reel-1");
    }

    @Test
    void withoutTargetTheRfqStaysOpenToTheCategory() {
        when(categoryRepository.findById("some-other-category")).thenReturn(Optional.of(cnc));

        RfqResponse response = service.submit("u-buyer", request());

        assertThat(response.getManufacturerId()).isNull();
        assertThat(response.getProductName()).isEqualTo("typed by the buyer");
        verify(notificationService, never()).notify(any(), any(), anyString(), anyString(), anyString());
    }
}
