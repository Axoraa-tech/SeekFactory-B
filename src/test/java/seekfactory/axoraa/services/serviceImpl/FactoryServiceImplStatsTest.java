package seekfactory.axoraa.services.serviceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import seekfactory.axoraa.dto.Response.factory.FactoryStatsResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Rfqs.Rfq;
import seekfactory.axoraa.entity.Rfqs.RfqQuote;
import seekfactory.axoraa.enums.RfqStatus;
import seekfactory.axoraa.enums.ViewEntityType;
import seekfactory.axoraa.repository.CategoryRepository;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.repository.Rfqs.RfqQuoteRepository;
import seekfactory.axoraa.repository.Rfqs.RfqRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.repository.ViewEventRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

/**
 * Seller dashboard KPIs must be derived purely from data: no placeholder fallbacks.
 */
@ExtendWith(MockitoExtension.class)
class FactoryServiceImplStatsTest {

    private static final String USER_ID = "usr-supp";
    private static final String MFR_ID = "mfr-1";

    @Mock private ManufacturerRepository manufacturerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ReelRepository reelRepository;
    @Mock private RfqRepository rfqRepository;
    @Mock private RfqQuoteRepository rfqQuoteRepository;
    @Mock private UserRepository userRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ViewEventRepository viewEventRepository;
    @Mock private ModelMapper modelMapper;

    @InjectMocks private FactoryServiceImpl service;

    private final List<Rfq> rfqs = new ArrayList<>();
    private final List<RfqQuote> quotes = new ArrayList<>();
    private Manufacturer manufacturer;
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        manufacturer = Manufacturer.builder().name("Test Factory").followerCount(42).build();
        manufacturer.setId(MFR_ID);
        when(manufacturerRepository.findByUserId(USER_ID)).thenReturn(Optional.of(manufacturer));
        when(productRepository.findByManufacturerIdAndIsActiveTrue(MFR_ID)).thenReturn(List.of());
        when(reelRepository.findByManufacturerIdOrderByCreatedAtDesc(MFR_ID)).thenReturn(List.of());
        when(rfqRepository.findAllByOrderByCreatedAtDesc()).thenReturn(rfqs);
        when(rfqQuoteRepository.findByManufacturerIdOrderByCreatedAtDesc(MFR_ID)).thenReturn(quotes);
        lenient().when(viewEventRepository.countByManufacturerIdAndEntityTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(MFR_ID), any(), any(), any())).thenReturn(0L);
    }

    private Rfq rfq(String id, RfqStatus status, Duration age) {
        Rfq rfq = Rfq.builder().status(status).build();
        rfq.setId(id);
        rfq.setCreatedAt(now.minus(age));
        rfqs.add(rfq);
        return rfq;
    }

    private void quote(Rfq rfq, Duration after) {
        RfqQuote quote = RfqQuote.builder().rfq(rfq).manufacturer(manufacturer)
                .quotePrice(BigDecimal.TEN).leadTimeDays(30).build();
        quote.setCreatedAt(rfq.getCreatedAt().plus(after));
        quotes.add(quote);
    }

    @Test
    void noActivityYieldsZerosAndNullsNotPlaceholders() {
        FactoryStatsResponse stats = service.getStats(USER_ID);

        assertThat(stats.getVideoSeekPlays()).isZero();
        assertThat(stats.getVideoPlaysChange()).isNull();
        assertThat(stats.getTotalProductViews()).isZero();
        assertThat(stats.getProductViewsChange()).isNull();
        assertThat(stats.getActiveRfqsCount()).isZero();
        assertThat(stats.getPendingRfqsCount()).isZero();
        assertThat(stats.getResponseRatePercent()).isNull();
        assertThat(stats.getAvgResponseTimeHours()).isNull();
        assertThat(stats.getFollowerCount()).isEqualTo(42);
    }

    @Test
    void rfqKpisReflectQuotesAndStatuses() {
        Rfq quotedFast = rfq("r1", RfqStatus.QUOTED, Duration.ofDays(5));
        Rfq quotedSlow = rfq("r2", RfqStatus.SUBMITTED, Duration.ofDays(4));
        rfq("r3", RfqStatus.SUBMITTED, Duration.ofDays(2));             // awaiting quote
        rfq("r4", RfqStatus.COMPLETED, Duration.ofDays(10));             // closed, unanswered
        rfq("r5", RfqStatus.CANCELLED, Duration.ofDays(3));              // cancelled before we answered: excluded
        rfq("r6", RfqStatus.SUBMITTED, Duration.ofDays(200));            // outside 90-day response window, still open

        quote(quotedFast, Duration.ofHours(2));
        quote(quotedSlow, Duration.ofHours(10));
        quote(quotedSlow, Duration.ofHours(30));                         // revision: first quote counts

        FactoryStatsResponse stats = service.getStats(USER_ID);

        // Active = r1, r2, r3, r6 ; awaiting = active without our quote = r3, r6
        assertThat(stats.getActiveRfqsCount()).isEqualTo(4);
        assertThat(stats.getPendingRfqsCount()).isEqualTo(2);
        // Response window (90d, cancelled-unanswered excluded): r1, r2, r3, r4 -> answered r1, r2
        assertThat(stats.getResponseRatePercent()).isEqualTo(50.0);
        // First-quote delays: 2h and 10h
        assertThat(stats.getAvgResponseTimeHours()).isEqualTo(6.0);
        assertThat(stats.getResponseWindowDays()).isEqualTo(90);
    }

    @Test
    void viewKpisCompareWithPreviousPeriod() {
        // Current window is the call whose "to" is (approximately) now; previous window ends at periodStart.
        when(viewEventRepository.countByManufacturerIdAndEntityTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(MFR_ID), eq(ViewEntityType.REEL), any(), any()))
                .thenAnswer(inv -> isCurrentWindow(inv.getArgument(3)) ? 150L : 100L);
        when(viewEventRepository.countByManufacturerIdAndEntityTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(MFR_ID), eq(ViewEntityType.PRODUCT), any(), any()))
                .thenAnswer(inv -> isCurrentWindow(inv.getArgument(3)) ? 7L : 0L);

        FactoryStatsResponse stats = service.getStats(USER_ID);

        assertThat(stats.getVideoSeekPlays()).isEqualTo(150);
        assertThat(stats.getVideoPlaysChange()).isEqualTo(50.0);
        assertThat(stats.getTotalProductViews()).isEqualTo(7);
        assertThat(stats.getProductViewsChange()).isNull();   // no baseline -> no invented %
        assertThat(stats.getPeriodDays()).isEqualTo(30);
    }

    private boolean isCurrentWindow(Instant to) {
        return Duration.between(to, Instant.now()).abs().toMinutes() < 1;
    }
}
