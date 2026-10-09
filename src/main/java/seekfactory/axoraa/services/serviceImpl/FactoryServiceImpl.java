package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import seekfactory.axoraa.dto.Request.manufacturer.ManufacturerUpdateRequest;
import seekfactory.axoraa.dto.Request.manufacturer.VerificationSubmitRequest;
import seekfactory.axoraa.dto.Request.product.ProductUpdateRequest;
import seekfactory.axoraa.dto.Request.reel.ReelUpdateRequest;
import seekfactory.axoraa.dto.Response.manufacturer.VerificationResponse;
import seekfactory.axoraa.dto.common.FactoryCertificate;
import seekfactory.axoraa.enums.VerificationStatus;
import seekfactory.axoraa.dto.Request.product.ProductCreateRequest;
import seekfactory.axoraa.dto.Request.reel.ReelCreateRequest;
import seekfactory.axoraa.dto.Request.rfq.RfqQuoteRequest;
import seekfactory.axoraa.dto.Response.factory.FactoryStatsResponse;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;
import seekfactory.axoraa.dto.Response.reel.ReelResponse;
import seekfactory.axoraa.dto.Response.rfq.RfqResponse;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.entity.Notification;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.entity.Rfqs.Rfq;
import seekfactory.axoraa.entity.Rfqs.RfqQuote;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.Currency;
import seekfactory.axoraa.enums.FeedTab;
import seekfactory.axoraa.enums.NotificationType;
import seekfactory.axoraa.enums.Incoterm;
import seekfactory.axoraa.enums.QuoteStatus;
import seekfactory.axoraa.enums.RfqStatus;
import seekfactory.axoraa.enums.ViewEntityType;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ForbiddenException;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.CategoryRepository;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.NotificationRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.repository.Comments.CommentRepository;
import seekfactory.axoraa.repository.Reels.ReelLikeRepository;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.repository.Reels.ReelSaveRepository;
import seekfactory.axoraa.repository.Rfqs.RfqQuoteRepository;
import seekfactory.axoraa.repository.Rfqs.RfqRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.repository.ViewEventRepository;
import seekfactory.axoraa.services.media.MediaTypes;
import seekfactory.axoraa.services.services.FactoryService;
import seekfactory.axoraa.services.services.MediaStorageService;
import seekfactory.axoraa.services.services.NotificationService;
import seekfactory.axoraa.services.services.ResponseMetrics;
import seekfactory.axoraa.utils.SlugUtils;
import seekfactory.axoraa.utils.CategoryTree;

import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FactoryServiceImpl implements FactoryService {

    private final ManufacturerRepository manufacturerRepository;
    private final ProductRepository productRepository;
    private final ReelRepository reelRepository;
    private final ReelLikeRepository reelLikeRepository;
    private final ReelSaveRepository reelSaveRepository;
    private final CommentRepository commentRepository;
    private final RfqRepository rfqRepository;
    private final RfqQuoteRepository rfqQuoteRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ViewEventRepository viewEventRepository;
    private final NotificationRepository notificationRepository;
    private final ModelMapper modelMapper;
    private final CategoryTree categoryTree;
    private final NotificationService notificationService;
    private final MediaStorageService mediaStorageService;

    /** View KPIs cover this window; change % compares with the window before it. */
    private static final Duration STATS_PERIOD = Duration.ofDays(30);
    /** Response rate / speed consider RFQs received in this window. */
    private static final Duration RESPONSE_WINDOW = Duration.ofDays(90);
    /** Weeks shown in the overview trend chart. */
    private static final int TREND_WEEKS = 12;
    /** RFQs still open for quoting. */
    private static final Set<RfqStatus> ACTIVE_RFQ_STATUSES =
            EnumSet.of(RfqStatus.SUBMITTED, RfqStatus.REVIEWING, RfqStatus.QUOTING, RfqStatus.QUOTED);

    @Override
    public ManufacturerResponse getProfile(String userId) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);
        return mapToManufacturerResponse(manufacturer);
    }

    @Override
    public ManufacturerResponse updateProfile(String userId, ManufacturerUpdateRequest request) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);

        if (request.getName() != null) manufacturer.setName(request.getName());
        if (request.getLogoUrl() != null) manufacturer.setLogoUrl(request.getLogoUrl());
        if (request.getCoverUrl() != null) manufacturer.setCoverUrl(request.getCoverUrl());
        if (request.getLocation() != null) manufacturer.setLocation(request.getLocation());
        if (request.getFactorySize() != null) manufacturer.setFactorySize(request.getFactorySize());
        if (request.getEmployees() != null) manufacturer.setEmployees(request.getEmployees());
        if (request.getDescription() != null) manufacturer.setDescription(request.getDescription());
        if (request.getChairmanName() != null) manufacturer.setChairmanName(request.getChairmanName());
        if (request.getExportCountries() != null) {
            manufacturer.setExportCountries(new HashSet<>(request.getExportCountries()));
        }
        if (request.getWebsiteUrl() != null) {
            manufacturer.setWebsiteUrl(normalizeWebsite(request.getWebsiteUrl()));
        }
        if (request.getAnnualTurnover() != null) {
            manufacturer.setAnnualTurnover(blankToNull(request.getAnnualTurnover()));
        }
        if (request.getProductionLines() != null) manufacturer.setProductionLines(request.getProductionLines());
        if (request.getYearsEstablished() != null) {
            if (request.getYearsEstablished() > Year.now().getValue()) {
                throw new BadRequestException("Year founded cannot be in the future");
            }
            manufacturer.setYearsEstablished(request.getYearsEstablished());
        }
        if (request.getCertifications() != null) {
            manufacturer.setCertifications(cleanCertifications(request.getCertifications()));
        }
        if (request.getCertificates() != null) {
            manufacturer.setCertificates(cleanCertificates(request.getCertificates()));
        }
        if (request.getCategoryIds() != null) {
            List<String> ids = request.getCategoryIds().stream().distinct().collect(Collectors.toList());
            List<Category> categories = categoryRepository.findAllById(ids);
            if (categories.size() != ids.size()) {
                throw new BadRequestException("One or more categories do not exist");
            }
            manufacturer.setCategories(new HashSet<>(categories));
        }

        Manufacturer saved = manufacturerRepository.save(manufacturer);
        return mapToManufacturerResponse(saved);
    }

    @Override
    public FactoryStatsResponse getStats(String userId) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);
        String mfrId = manufacturer.getId();

        List<Product> products = productRepository.findByManufacturerIdAndIsActiveTrue(mfrId);
        List<Reel> seeks = reelRepository.findByManufacturerIdOrderByCreatedAtDesc(mfrId);

        // ── Views: current window vs the window before it ──
        Instant now = Instant.now();
        Instant periodStart = now.minus(STATS_PERIOD);
        Instant previousStart = periodStart.minus(STATS_PERIOD);
        long reelViews = countViews(mfrId, ViewEntityType.REEL, periodStart, now);
        long reelViewsBefore = countViews(mfrId, ViewEntityType.REEL, previousStart, periodStart);
        long productViews = countViews(mfrId, ViewEntityType.PRODUCT, periodStart, now);
        long productViewsBefore = countViews(mfrId, ViewEntityType.PRODUCT, previousStart, periodStart);

        // ── RFQs: same matching rule as the seller's RFQ list ──
        List<Rfq> matched = findMatchedRfqs(manufacturer);

        Map<String, Instant> firstQuoteAt = firstQuoteTimes(mfrId);

        List<Rfq> active = matched.stream()
                .filter(r -> r.getStatus() != null && ACTIVE_RFQ_STATUSES.contains(r.getStatus()))
                .collect(Collectors.toList());
        long awaitingQuote = active.stream().filter(r -> !firstQuoteAt.containsKey(r.getId())).count();

        ResponseMetrics metrics = responseMetrics(matched, firstQuoteAt, now);

        return FactoryStatsResponse.builder()
                .periodDays((int) STATS_PERIOD.toDays())
                .videoSeekPlays(reelViews)
                .videoPlaysChange(percentChange(reelViews, reelViewsBefore))
                .totalProductViews(productViews)
                .productViewsChange(percentChange(productViews, productViewsBefore))
                .factoryProfileVisits(0)
                .profileVisitsChange(null)
                .activeRfqsCount(active.size())
                .pendingRfqsCount((int) awaitingQuote)
                .responseRatePercent(metrics.responseRatePercent())
                .avgResponseTimeHours(metrics.avgResponseTimeHours())
                .responseWindowDays((int) RESPONSE_WINDOW.toDays())
                .followerCount(manufacturer.getFollowerCount() != null ? manufacturer.getFollowerCount() : 0)
                .totalProductsCount(products.size())
                .totalSeeksCount(seeks.size())
                .weeklyTrend(weeklyTrend(mfrId, matched, now))
                .build();
    }

    /** Last TREND_WEEKS weeks of seek views, product views and routed RFQs. */
    private List<FactoryStatsResponse.TrendPoint> weeklyTrend(String mfrId, List<Rfq> matched, Instant now) {
        LocalDate thisMonday = now.atZone(ZoneOffset.UTC).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate firstMonday = thisMonday.minusWeeks(TREND_WEEKS - 1);
        Instant since = firstMonday.atStartOfDay(ZoneOffset.UTC).toInstant();

        long[] seekViews = new long[TREND_WEEKS];
        long[] productViews = new long[TREND_WEEKS];
        long[] rfqCounts = new long[TREND_WEEKS];

        for (Object[] row : viewEventRepository.findTypeAndTimeSince(mfrId, since)) {
            int week = weekIndex(firstMonday, (Instant) row[1]);
            if (week < 0) continue;
            if (row[0] == ViewEntityType.REEL) seekViews[week]++;
            else if (row[0] == ViewEntityType.PRODUCT) productViews[week]++;
        }
        for (Rfq rfq : matched) {
            int week = rfq.getCreatedAt() == null ? -1 : weekIndex(firstMonday, rfq.getCreatedAt());
            if (week >= 0) rfqCounts[week]++;
        }

        List<FactoryStatsResponse.TrendPoint> points = new ArrayList<>();
        for (int i = 0; i < TREND_WEEKS; i++) {
            points.add(FactoryStatsResponse.TrendPoint.builder()
                    .weekStart(firstMonday.plusWeeks(i).toString())
                    .seekViews(seekViews[i])
                    .productViews(productViews[i])
                    .rfqs(rfqCounts[i])
                    .build());
        }
        return points;
    }

    /** 0-based week bucket of {@code at}, or -1 when outside the trend window. */
    private static int weekIndex(LocalDate firstMonday, Instant at) {
        long days = ChronoUnit.DAYS.between(firstMonday, at.atZone(ZoneOffset.UTC).toLocalDate());
        int week = (int) Math.floorDiv(days, 7);
        return week >= 0 && week < TREND_WEEKS ? week : -1;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseMetrics getResponseMetrics(Manufacturer manufacturer) {
        return responseMetrics(findMatchedRfqs(manufacturer), firstQuoteTimes(manufacturer.getId()), Instant.now());
    }

    /** Earliest quote this factory sent, per RFQ id. */
    private Map<String, Instant> firstQuoteTimes(String manufacturerId) {
        Map<String, Instant> firstQuoteAt = new HashMap<>();
        for (RfqQuote quote : rfqQuoteRepository.findByManufacturerIdOrderByCreatedAtDesc(manufacturerId)) {
            firstQuoteAt.merge(quote.getRfq().getId(), quote.getCreatedAt(),
                    (a, b) -> a.isBefore(b) ? a : b);
        }
        return firstQuoteAt;
    }

    /** Responsiveness over RFQs received in the response window. */
    private static ResponseMetrics responseMetrics(List<Rfq> matched, Map<String, Instant> firstQuoteAt, Instant now) {
        Instant responseSince = now.minus(RESPONSE_WINDOW);
        List<Rfq> received = matched.stream()
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(responseSince))
                .filter(r -> r.getStatus() != RfqStatus.CANCELLED || firstQuoteAt.containsKey(r.getId()))
                .collect(Collectors.toList());
        List<Rfq> answered = received.stream()
                .filter(r -> firstQuoteAt.containsKey(r.getId()))
                .collect(Collectors.toList());

        Double responseRate = received.isEmpty() ? null
                : round1(100.0 * answered.size() / received.size());
        Double avgResponseHours = answered.isEmpty() ? null
                : round1(answered.stream()
                        .mapToLong(r -> Math.max(0, Duration.between(r.getCreatedAt(), firstQuoteAt.get(r.getId())).toMinutes()))
                        .average()
                        .orElse(0) / 60.0);
        return new ResponseMetrics(responseRate, avgResponseHours);
    }

    private long countViews(String manufacturerId, ViewEntityType type, Instant from, Instant to) {
        return viewEventRepository
                .countByManufacturerIdAndEntityTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        manufacturerId, type, from, to);
    }

    /** % change vs the previous period; null when there is no baseline to compare with. */
    private static Double percentChange(long current, long previous) {
        if (previous == 0) return null;
        return round1(100.0 * (current - previous) / previous);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getProducts(String userId) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);
        return productRepository.findByManufacturerIdAndIsActiveTrue(manufacturer.getId())
                .stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ProductResponse addProduct(String userId, ProductCreateRequest request) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BadRequestException("Unknown category: " + request.getCategoryId()));
        List<String> gallery = request.getImageUrls() != null && !request.getImageUrls().isEmpty()
                ? request.getImageUrls() : List.of(request.getImageUrl());
        gallery = validGallery(gallery);

        // Random suffix: the old millisecond counter could repeat and hit the unique constraint (500)
        String uniqueSlug = SlugUtils.uniqueSlug(request.getName(), "product");

        Product product = Product.builder()
                .name(request.getName())
                .slug(uniqueSlug)
                .manufacturer(manufacturer)
                .category(category)
                .imageUrl(gallery.get(0))
                .imageUrls(new ArrayList<>(gallery))
                .description(request.getDescription())
                .priceInr(request.getPriceInr())
                .unit(request.getUnit() != null ? request.getUnit() : "Set")
                .moq(request.getMoq() != null ? request.getMoq() : "1 Set")
                .specs(request.getSpecs() != null ? request.getSpecs() : new HashMap<>())
                .isActive(true)
                .listed(true)
                .build();
        applyDatasheet(product, request.getDatasheetUrl(), request.getDatasheetName());

        Product saved = productRepository.save(product);
        return mapToProductResponse(saved);
    }

    @Override
    public void deleteProduct(String userId, String productId) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        if (!product.getManufacturer().getId().equals(manufacturer.getId())) {
            throw new ForbiddenException("You cannot delete products belonging to another factory");
        }

        product.setIsActive(false);
        productRepository.save(product);
    }

    @Override
    public ProductResponse updateProduct(String userId, String productId, ProductUpdateRequest request) {
        Product product = ownedProduct(userId, productId);

        if (request.getName() != null) product.setName(request.getName().trim());
        if (request.getImageUrls() != null) {
            List<String> gallery = validGallery(request.getImageUrls());
            product.setImageUrl(gallery.get(0));
            product.setImageUrls(new ArrayList<>(gallery));
        }
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getPriceInr() != null) product.setPriceInr(request.getPriceInr());
        if (request.getUnit() != null && !request.getUnit().isBlank()) product.setUnit(request.getUnit().trim());
        if (request.getMoq() != null && !request.getMoq().isBlank()) product.setMoq(request.getMoq().trim());
        if (request.getCategoryId() != null) {
            product.setCategory(categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new BadRequestException("Unknown category: " + request.getCategoryId())));
        }
        if (request.getSpecs() != null) product.setSpecs(new HashMap<>(request.getSpecs()));
        if (request.getDatasheetUrl() != null) {
            applyDatasheet(product, request.getDatasheetUrl(), request.getDatasheetName());
        }

        return mapToProductResponse(productRepository.save(product));
    }

    @Override
    public ProductResponse setProductListed(String userId, String productId, boolean listed) {
        Product product = ownedProduct(userId, productId);
        product.setListed(listed);
        return mapToProductResponse(productRepository.save(product));
    }

    /** A live (not deleted) product of this supplier's factory. */
    private Product ownedProduct(String userId, String productId) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);
        Product product = productRepository.findById(productId)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
        if (!product.getManufacturer().getId().equals(manufacturer.getId())) {
            throw new ForbiddenException("You cannot change products belonging to another factory");
        }
        return product;
    }

    /** 1-8 safe media URLs, de-duplicated, order kept. */
    private static List<String> validGallery(List<String> urls) {
        List<String> gallery = urls == null ? List.of() : urls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(u -> !u.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        if (gallery.isEmpty()) throw new BadRequestException("A product needs at least one image");
        if (gallery.size() > 8) throw new BadRequestException("At most 8 images per product");
        gallery.forEach(u -> requireMediaUrl(u, "imageUrls"));
        return gallery;
    }

    /** Sets or (with a blank URL) clears the product's PDF datasheet. */
    private static void applyDatasheet(Product product, String url, String name) {
        if (url == null || url.isBlank()) {
            product.setDatasheetUrl(null);
            product.setDatasheetName(null);
            return;
        }
        requireMediaUrl(url, "datasheetUrl");
        if (!url.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new BadRequestException("Datasheet must be a PDF");
        }
        String cleanName = name == null || name.isBlank() ? "Datasheet.pdf"
                : name.replaceAll("[\\\\/\\r\\n]", "_").trim();
        product.setDatasheetUrl(url);
        product.setDatasheetName(cleanName.length() > 255 ? cleanName.substring(0, 255) : cleanName);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReelResponse> getSeeks(String userId) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);
        return withRealEngagement(reelRepository.findByManufacturerIdOrderByCreatedAtDesc(manufacturer.getId()));
    }

    /**
     * Seller-facing seek numbers counted from the event tables (distinct-viewer views, likes,
     * saves, comments) instead of the reels.*_count columns, which were seeded with demo values.
     */
    private List<ReelResponse> withRealEngagement(List<Reel> reels) {
        if (reels.isEmpty()) return new ArrayList<>();
        List<String> ids = reels.stream().map(Reel::getId).collect(Collectors.toList());
        Map<String, Long> views = toCounts(viewEventRepository.countByEntityIds(ViewEntityType.REEL, ids));
        Map<String, Long> likes = toCounts(reelLikeRepository.countByReelIds(ids));
        Map<String, Long> saves = toCounts(reelSaveRepository.countByReelIds(ids));
        Map<String, Long> comments = toCounts(commentRepository.countByReelIds(ids));
        return reels.stream().map(reel -> {
            ReelResponse response = mapToReelResponse(reel);
            response.setViews(views.getOrDefault(reel.getId(), 0L));
            response.setLikes(likes.getOrDefault(reel.getId(), 0L).intValue());
            response.setSaves(saves.getOrDefault(reel.getId(), 0L).intValue());
            response.setComments(comments.getOrDefault(reel.getId(), 0L).intValue());
            return response;
        }).collect(Collectors.toList());
    }

    private ReelResponse withRealEngagement(Reel reel) {
        return withRealEngagement(List.of(reel)).get(0);
    }

    private static Map<String, Long> toCounts(List<Object[]> rows) {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : rows) counts.put((String) row[0], ((Number) row[1]).longValue());
        return counts;
    }

    @Override
    public ReelResponse addSeek(String userId, ReelCreateRequest request) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);
        requireMediaUrl(request.getPosterUrl(), "posterUrl");
        if (request.getVideoUrl() != null && !request.getVideoUrl().isBlank()) {
            requireMediaUrl(request.getVideoUrl(), "videoUrl");
        }

        Set<Product> taggedProducts = taggableProducts(manufacturer, request.getProductIds());

        Set<String> tags = request.getHashtags() != null ? new HashSet<>(request.getHashtags()) : new HashSet<>();
        tags.add("#manufacturing");
        tags.add("#oem");

        Reel reel = Reel.builder()
                .manufacturer(manufacturer)
                .title(request.getTitle())
                .description(request.getDescription())
                .posterUrl(request.getPosterUrl())
                .videoUrl(request.getVideoUrl())
                .durationSec(request.getDurationSec() != null ? request.getDurationSec() : 30)
                .startSec(0)
                .viewsCount(0L)
                .likesCount(0)
                .commentsCount(0)
                .sharesCount(0)
                .savesCount(0)
                .feedTab(FeedTab.FOR_YOU)
                .hashtags(tags)
                .products(taggedProducts)
                .build();

        Reel saved = reelRepository.save(reel);
        return withRealEngagement(saved);
    }

    @Override
    public void deleteSeek(String userId, String reelId) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);
        Reel reel = reelRepository.findById(reelId)
                .orElseThrow(() -> new ResourceNotFoundException("Reel", "id", reelId));

        if (!reel.getManufacturer().getId().equals(manufacturer.getId())) {
            throw new ForbiddenException("You cannot delete reels belonging to another factory");
        }

        String videoUrl = reel.getVideoUrl();
        boolean shared = videoUrl == null || reelRepository.existsByVideoUrlAndIdNot(videoUrl, reel.getId());
        reelRepository.delete(reel);
        if (!shared && videoUrl.startsWith(MediaTypes.PUBLIC_PATH)) {
            // Remove the video file too, once the delete is committed. Thumbnails are left alone:
            // they are often product photos still in use.
            String key = videoUrl.substring(MediaTypes.PUBLIC_PATH.length());
            afterCommit(() -> mediaStorageService.delete(key));
        }
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    @Override
    public ReelResponse updateSeek(String userId, String reelId, ReelUpdateRequest request) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);
        Reel reel = ownedReel(manufacturer, reelId);

        if (request.getTitle() != null) reel.setTitle(request.getTitle().trim());
        if (request.getDescription() != null) reel.setDescription(request.getDescription());
        if (request.getPosterUrl() != null) {
            requireMediaUrl(request.getPosterUrl(), "posterUrl");
            reel.setPosterUrl(request.getPosterUrl());
        }
        if (request.getVideoUrl() != null && !request.getVideoUrl().isBlank()) {
            requireMediaUrl(request.getVideoUrl(), "videoUrl");
            reel.setVideoUrl(request.getVideoUrl());
        }
        if (request.getDurationSec() != null && request.getDurationSec() > 0) {
            reel.setDurationSec(request.getDurationSec());
        }
        if (request.getHashtags() != null) {
            reel.setHashtags(request.getHashtags().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .map(t -> t.startsWith("#") ? t : "#" + t)
                    .collect(Collectors.toCollection(HashSet::new)));
        }
        if (request.getProductIds() != null) {
            reel.setProducts(taggableProducts(manufacturer, request.getProductIds()));
        }
        return withRealEngagement(reelRepository.save(reel));
    }

    @Override
    public ReelResponse setSeekListed(String userId, String reelId, boolean listed) {
        Reel reel = ownedReel(getOrCreateManufacturer(userId), reelId);
        reel.setListed(listed);
        return withRealEngagement(reelRepository.save(reel));
    }

    private Reel ownedReel(Manufacturer manufacturer, String reelId) {
        Reel reel = reelRepository.findById(reelId)
                .orElseThrow(() -> new ResourceNotFoundException("Reel", "id", reelId));
        if (!reel.getManufacturer().getId().equals(manufacturer.getId())) {
            throw new ForbiddenException("You cannot change seeks belonging to another factory");
        }
        return reel;
    }

    /** Tagged products must be this factory's own, not-deleted products. */
    private Set<Product> taggableProducts(Manufacturer manufacturer, List<String> productIds) {
        Set<Product> tagged = new HashSet<>();
        if (productIds == null || productIds.isEmpty()) return tagged;
        List<Product> found = productRepository.findAllById(productIds);
        for (Product product : found) {
            if (!product.getManufacturer().getId().equals(manufacturer.getId())
                    || !Boolean.TRUE.equals(product.getIsActive())) {
                throw new BadRequestException("Product " + product.getId() + " cannot be tagged on this seek");
            }
        }
        if (found.size() != new HashSet<>(productIds).size()) {
            throw new BadRequestException("One or more tagged products do not exist");
        }
        tagged.addAll(found);
        return tagged;
    }

    // ─── Verification ──────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public VerificationResponse getVerification(String userId) {
        return toVerification(getOrCreateManufacturer(userId));
    }

    @Override
    public VerificationResponse submitVerification(String userId, VerificationSubmitRequest request) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);
        if (manufacturer.getVerificationStatus() == VerificationStatus.APPROVED) {
            throw new BadRequestException("Your factory is already verified");
        }
        manufacturer.setCompanyRegNumber(request.getCompanyRegNumber().trim());
        manufacturer.setTaxId(blankToNull(request.getTaxId()));
        manufacturer.setRegistrationDate(blankToNull(request.getRegistrationDate()));
        manufacturer.setFactoryAddress(request.getFactoryAddress().trim());
        if (request.getCountry() != null && !request.getCountry().isBlank()) {
            manufacturer.setCountry(request.getCountry().trim());
        }
        if (request.getCertifications() != null) {
            manufacturer.setCertifications(cleanCertifications(request.getCertifications()));
        }
        // (Re)submission puts the factory back in the admin review queue
        manufacturer.setVerificationStatus(VerificationStatus.PENDING);
        manufacturer.setSubmittedAt(Instant.now());
        manufacturer.setRejectionReason(null);
        manufacturer.setReviewedAt(null);
        manufacturer.setReviewedBy(null);
        log.info("Factory {} submitted verification", manufacturer.getId());
        return toVerification(manufacturerRepository.save(manufacturer));
    }

    private static VerificationResponse toVerification(Manufacturer m) {
        return VerificationResponse.builder()
                .status(m.getVerificationStatus() != null ? m.getVerificationStatus().name() : VerificationStatus.PENDING.name())
                .submitted(m.getSubmittedAt() != null)
                .submittedAt(m.getSubmittedAt() != null ? m.getSubmittedAt().toString() : null)
                .reviewedAt(m.getReviewedAt() != null ? m.getReviewedAt().toString() : null)
                .rejectionReason(m.getRejectionReason())
                .companyRegNumber(m.getCompanyRegNumber())
                .taxId(m.getTaxId())
                .registrationDate(m.getRegistrationDate())
                .factoryAddress(m.getFactoryAddress())
                .certifications(new ArrayList<>(m.getCertifications()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public void assertRfqRouted(String userId, String rfqId) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);
        boolean routed = findMatchedRfqs(manufacturer).stream().anyMatch(r -> r.getId().equals(rfqId));
        if (!routed) {
            throw new ResourceNotFoundException("Rfq", "id", rfqId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<RfqResponse> getRfqs(String userId) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);

        // This factory's current quote per RFQ (newest wins if legacy duplicates exist)
        Map<String, RfqQuote> myQuotes = new HashMap<>();
        for (RfqQuote quote : rfqQuoteRepository.findByManufacturerIdOrderByCreatedAtDesc(manufacturer.getId())) {
            myQuotes.putIfAbsent(quote.getRfq().getId(), quote);
        }

        return findMatchedRfqs(manufacturer).stream()
                .map(rfq -> withSellerView(mapToRfqResponse(rfq), rfq, myQuotes.get(rfq.getId())))
                .collect(Collectors.toList());
    }

    /**
     * RFQs routed to a factory: those in its categories (including their subcategories),
     * or every RFQ if it has no categories.
     * Shared by the RFQ list and the dashboard stats so the two always agree.
     */
    private List<Rfq> findMatchedRfqs(Manufacturer manufacturer) {
        if (manufacturer.getCategories().isEmpty()) {
            return rfqRepository.findAllByOrderByCreatedAtDesc();
        }
        // Buyers usually file RFQs under a top-level category while factories may list either level:
        // match the factory's categories, their subcategories and their parents.
        // Uncategorised RFQs go to every factory.
        Set<String> categoryIds = new LinkedHashSet<>(categoryTree.withAncestors(manufacturer.getCategories()));
        for (Category category : manufacturer.getCategories()) {
            categoryRepository.findByParentIdOrderByNameAsc(category.getId())
                    .forEach(child -> categoryIds.add(child.getId()));
        }
        return rfqRepository.findByCategoryIdInOrCategoryIsNullOrderByCreatedAtDesc(new ArrayList<>(categoryIds));
    }

    @Override
    public void submitQuote(String userId, String rfqId, RfqQuoteRequest request) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);
        Rfq rfq = rfqRepository.findByIdForUpdate(rfqId)
                .orElseThrow(() -> new ResourceNotFoundException("Rfq", "id", rfqId));
        // Only RFQs routed to this factory may be quoted (same rule as assertRfqRouted)
        if (findMatchedRfqs(manufacturer).stream().noneMatch(r -> r.getId().equals(rfqId))) {
            throw new ResourceNotFoundException("Rfq", "id", rfqId);
        }
        if (rfq.getStatus() != null && !ACTIVE_RFQ_STATUSES.contains(rfq.getStatus())) {
            throw new BadRequestException("This RFQ is closed (" + rfq.getStatus().name() + ") and no longer accepts quotes");
        }

        // Default INR (the UI's currency); reject unknown codes instead of silently using USD
        Currency currency = Currency.INR;
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            try {
                currency = Currency.valueOf(request.getCurrency().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unsupported currency: " + request.getCurrency());
            }
        }
        String incoterm = null;
        if (request.getIncoterm() != null && !request.getIncoterm().isBlank()) {
            try {
                incoterm = Incoterm.valueOf(request.getIncoterm().trim().toUpperCase(Locale.ROOT)).name();
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unsupported incoterm: " + request.getIncoterm());
            }
        }

        // One quote per factory per RFQ: revisions update it in place, keeping the original
        // created_at so response-speed analytics measure the first response.
        RfqQuote quote = rfqQuoteRepository.findByRfqIdOrderByCreatedAtDesc(rfqId).stream()
                .filter(q -> q.getManufacturer().getId().equals(manufacturer.getId()))
                .findFirst()
                .orElse(null);
        boolean revision = quote != null;
        if (quote == null) {
            quote = RfqQuote.builder().rfq(rfq).manufacturer(manufacturer).status(QuoteStatus.PENDING).build();
        }
        quote.setQuotePrice(request.getQuotePrice());
        quote.setCurrency(currency);
        quote.setLeadTimeDays(request.getLeadTimeDays());
        quote.setIncoterm(incoterm);
        quote.setNotes(request.getNotes());
        quote.setAttachmentUrl(request.getAttachmentUrl());
        rfqQuoteRepository.save(quote);

        // "QUOTED" = at least one supplier quote received (see RfqStatus)
        if (rfq.getStatus() == null || rfq.getStatus() == RfqStatus.SUBMITTED
                || rfq.getStatus() == RfqStatus.REVIEWING || rfq.getStatus() == RfqStatus.QUOTING) {
            rfq.setStatus(RfqStatus.QUOTED);
            rfqRepository.save(rfq);
        }

        if (rfq.getUser() != null) {
            notificationRepository.save(Notification.builder()
                    .user(rfq.getUser())
                    .title((revision ? "Quotation updated for " : "New quotation for ") + rfq.getProductName())
                    .body(manufacturer.getName() + " quoted " + currency.name() + " "
                            + request.getQuotePrice().toPlainString() + ", lead time "
                            + request.getLeadTimeDays() + " days"
                            + (incoterm != null ? " (" + incoterm + ")" : "") + ".")
                    .notificationType(NotificationType.QUOTE)
                    .referenceId(rfq.getId())
                    .build());
        }
    }

    /** Adds buyer identity and this factory's quotation to an RFQ for the seller view. */
    private RfqResponse withSellerView(RfqResponse response, Rfq rfq, RfqQuote myQuote) {
        User buyer = rfq.getUser();
        if (buyer != null) {
            response.setBuyerName(buyer.getName());
            response.setBuyerCountry(buyer.getCountry());
            response.setBuyerAvatarUrl(buyer.getAvatarUrl());
        }
        if (myQuote != null) {
            response.setMyQuotePrice(myQuote.getQuotePrice());
            response.setMyQuoteCurrency(myQuote.getCurrency() != null ? myQuote.getCurrency().name() : null);
            response.setMyQuoteLeadTimeDays(myQuote.getLeadTimeDays());
            response.setMyQuoteIncoterm(myQuote.getIncoterm());
            response.setMyQuoteNotes(myQuote.getNotes());
            response.setMyQuotedAt(myQuote.getCreatedAt() != null ? myQuote.getCreatedAt().toString() : null);
        }
        return response;
    }

    // ─── Helpers ──────────────────────────────────────────────

    /**
     * Media references must be http(s) URLs or server-relative paths (e.g. /api/v1/media/...),
     * never javascript:/data: URIs that would be rendered into buyer pages.
     */
    private static void requireMediaUrl(String url, String field) {
        if (url == null || !(url.startsWith("/") || url.matches("(?i)^https?://\\S+$")) || url.startsWith("//")) {
            throw new BadRequestException(field + " must be an http(s) URL or an uploaded media path");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Public website: http(s) only (rendered as a link on buyer pages); blank clears it. */
    private static String normalizeWebsite(String raw) {
        String url = blankToNull(raw);
        if (url == null) return null;
        if (!url.matches("(?i)^https?://.*")) url = "https://" + url;
        if (!url.matches("(?i)^https?://[a-z0-9.-]+\\.[a-z]{2,}(:\\d+)?([/?#]\\S*)?$")) {
            throw new BadRequestException("Enter a valid website URL, e.g. https://example.com");
        }
        return url;
    }

    /**
     * Seller-supplied certificate documents: safe image URLs, ids assigned where missing,
     * and never marked verified (only SeekFactory review may set that).
     */
    private static List<FactoryCertificate> cleanCertificates(List<FactoryCertificate> certificates) {
        List<FactoryCertificate> clean = new ArrayList<>();
        for (FactoryCertificate cert : certificates) {
            if (cert == null) continue;
            requireMediaUrl(cert.getImageUrl(), "certificate imageUrl");
            clean.add(FactoryCertificate.builder()
                    .id(cert.getId() != null && !cert.getId().isBlank() ? cert.getId() : "cert-" + UUID.randomUUID())
                    .title(cert.getTitle().trim())
                    .issuer(cert.getIssuer().trim())
                    .certNumber(blankToNull(cert.getCertNumber()))
                    .issueDate(blankToNull(cert.getIssueDate()))
                    .expiryDate(blankToNull(cert.getExpiryDate()))
                    .imageUrl(cert.getImageUrl())
                    .category(blankToNull(cert.getCategory()))
                    .verified(false)
                    .build());
        }
        return clean;
    }

    private static Set<String> cleanCertifications(List<String> certifications) {
        return certifications.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(c -> !c.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Manufacturer getOrCreateManufacturer(String userId) {
        return manufacturerRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

                    String company = user.getCompanyName() != null && !user.getCompanyName().isBlank()
                            ? user.getCompanyName() : user.getName();
                    String slug = company.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-" + user.getId().substring(0, 6);

                    Manufacturer m = Manufacturer.builder()
                            .user(user)
                            .name(company)
                            .slug(slug)
                            .country(user.getCountry() != null ? user.getCountry() : "India")
                            .location("Industrial Estate, Bengaluru")
                            .verified(true)
                            .premium(false)
                            .yearsEstablished(2012)
                            .factorySize("35,000 sq.m")
                            .employees("250+ Engineers")
                            .description("Certified precision manufacturing, CNC machining, and industrial fabrication.")
                            .build();

                    return manufacturerRepository.save(m);
                });
    }

    private ManufacturerResponse mapToManufacturerResponse(Manufacturer m) {
        ManufacturerResponse res = modelMapper.map(m, ManufacturerResponse.class);
        res.setExportCountries(new ArrayList<>(m.getExportCountries()));
        res.setCategoryIds(m.getCategories().stream().map(Category::getId).collect(Collectors.toList()));
        res.setCertifications(new ArrayList<>(m.getCertifications()));
        res.setCertificates(new ArrayList<>(m.getCertificates() != null ? m.getCertificates() : List.of()));
        return res;
    }

    private ProductResponse mapToProductResponse(Product p) {
        ProductResponse res = modelMapper.map(p, ProductResponse.class);
        res.setManufacturerId(p.getManufacturer().getId());
        res.setCategoryId(p.getCategory() != null ? p.getCategory().getId() : null);
        res.setImageUrls(p.gallery());
        res.setListed(!Boolean.FALSE.equals(p.getListed()));
        return res;
    }

    // Explicit mapping: entity counters are named viewsCount/likesCount/... while the response
    // uses views/likes/..., which ModelMapper silently skipped (seller table showed 0 views).
    private ReelResponse mapToReelResponse(Reel r) {
        return ReelResponse.builder()
                .id(r.getId())
                .manufacturerId(r.getManufacturer().getId())
                .title(r.getTitle())
                .description(r.getDescription())
                .hashtags(new ArrayList<>(r.getHashtags()))
                .posterUrl(r.getPosterUrl())
                .videoUrl(r.getVideoUrl())
                .durationSec(r.getDurationSec())
                .startSec(r.getStartSec())
                .views(r.getViewsCount())
                .likes(r.getLikesCount())
                .comments(r.getCommentsCount())
                .shares(r.getSharesCount())
                .saves(r.getSavesCount())
                .tab(r.getFeedTab().name().toLowerCase().replace("_", "-"))
                .productIds(r.getProducts().stream()
                        .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                        .map(Product::getId)
                        .collect(Collectors.toList()))
                .listed(!Boolean.FALSE.equals(r.getListed()))
                .build();
    }

    private RfqResponse mapToRfqResponse(Rfq r) {
        return RfqResponse.builder()
                .id(r.getId())
                .referenceNumber(r.getReferenceNumber())
                .productName(r.getProductName())
                .categoryId(r.getCategory() != null ? r.getCategory().getId() : null)
                .quantity(r.getQuantity())
                .unit(r.getUnit())
                .targetPrice(r.getTargetPrice())
                .currency(r.getCurrency() != null ? r.getCurrency().name() : "INR")
                .incoterm(r.getIncoterm() != null ? r.getIncoterm().name() : "FOB")
                .companyName(r.getCompanyName())
                .details(r.getDetails())
                .attachmentName(r.getAttachmentName())
                .attachmentSize(r.getAttachmentSize())
                .attachmentUrl(r.getAttachmentUrl())
                .status(r.getStatus() != null ? r.getStatus().name() : "SUBMITTED")
                .createdAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : null)
                .build();
    }
}