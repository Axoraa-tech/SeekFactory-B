package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Request.manufacturer.ManufacturerUpdateRequest;
import seekfactory.axoraa.dto.Request.product.ProductCreateRequest;
import seekfactory.axoraa.dto.Request.reel.ReelCreateRequest;
import seekfactory.axoraa.dto.Request.rfq.RfqQuoteRequest;
import seekfactory.axoraa.dto.Response.factory.FactoryStatsResponse;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;
import seekfactory.axoraa.dto.Response.reel.ReelResponse;
import seekfactory.axoraa.dto.Response.rfq.RfqResponse;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.entity.Rfqs.Rfq;
import seekfactory.axoraa.entity.Rfqs.RfqQuote;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.Currency;
import seekfactory.axoraa.enums.FeedTab;
import seekfactory.axoraa.enums.QuoteStatus;
import seekfactory.axoraa.enums.RfqStatus;
import seekfactory.axoraa.enums.ViewEntityType;
import seekfactory.axoraa.exceptions.ForbiddenException;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.CategoryRepository;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.repository.Rfqs.RfqQuoteRepository;
import seekfactory.axoraa.repository.Rfqs.RfqRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.repository.ViewEventRepository;
import seekfactory.axoraa.services.services.FactoryService;

import java.time.Duration;
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
    private final RfqRepository rfqRepository;
    private final RfqQuoteRepository rfqQuoteRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ViewEventRepository viewEventRepository;
    private final ModelMapper modelMapper;

    /** View KPIs cover this window; change % compares with the window before it. */
    private static final Duration STATS_PERIOD = Duration.ofDays(30);
    /** Response rate / speed consider RFQs received in this window. */
    private static final Duration RESPONSE_WINDOW = Duration.ofDays(90);
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

        // Earliest quote this factory sent per RFQ
        Map<String, Instant> firstQuoteAt = new HashMap<>();
        for (RfqQuote quote : rfqQuoteRepository.findByManufacturerIdOrderByCreatedAtDesc(mfrId)) {
            firstQuoteAt.merge(quote.getRfq().getId(), quote.getCreatedAt(),
                    (a, b) -> a.isBefore(b) ? a : b);
        }

        List<Rfq> active = matched.stream()
                .filter(r -> r.getStatus() != null && ACTIVE_RFQ_STATUSES.contains(r.getStatus()))
                .collect(Collectors.toList());
        long awaitingQuote = active.stream().filter(r -> !firstQuoteAt.containsKey(r.getId())).count();

        // ── Responsiveness over RFQs received in the response window ──
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
                .responseRatePercent(responseRate)
                .avgResponseTimeHours(avgResponseHours)
                .responseWindowDays((int) RESPONSE_WINDOW.toDays())
                .followerCount(manufacturer.getFollowerCount() != null ? manufacturer.getFollowerCount() : 0)
                .totalProductsCount(products.size())
                .totalSeeksCount(seeks.size())
                .build();
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
        requireMediaUrl(request.getImageUrl(), "imageUrl");

        String baseSlug = request.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        String uniqueSlug = baseSlug + "-" + (System.currentTimeMillis() % 100000);

        Product product = Product.builder()
                .name(request.getName())
                .slug(uniqueSlug)
                .manufacturer(manufacturer)
                .category(category)
                .imageUrl(request.getImageUrl())
                .description(request.getDescription())
                .priceInr(request.getPriceInr())
                .unit(request.getUnit() != null ? request.getUnit() : "Set")
                .moq(request.getMoq() != null ? request.getMoq() : "1 Set")
                .specs(request.getSpecs() != null ? request.getSpecs() : new HashMap<>())
                .isActive(true)
                .build();

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
    @Transactional(readOnly = true)
    public List<ReelResponse> getSeeks(String userId) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);
        return reelRepository.findByManufacturerIdOrderByCreatedAtDesc(manufacturer.getId())
                .stream()
                .map(this::mapToReelResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ReelResponse addSeek(String userId, ReelCreateRequest request) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);
        requireMediaUrl(request.getPosterUrl(), "posterUrl");
        if (request.getVideoUrl() != null && !request.getVideoUrl().isBlank()) {
            requireMediaUrl(request.getVideoUrl(), "videoUrl");
        }

        // Tagged products must be this factory's own, still-listed products
        Set<Product> taggedProducts = new HashSet<>();
        if (request.getProductIds() != null && !request.getProductIds().isEmpty()) {
            List<Product> found = productRepository.findAllById(request.getProductIds());
            for (Product product : found) {
                if (!product.getManufacturer().getId().equals(manufacturer.getId())
                        || !Boolean.TRUE.equals(product.getIsActive())) {
                    throw new BadRequestException("Product " + product.getId() + " cannot be tagged on this seek");
                }
            }
            if (found.size() != new HashSet<>(request.getProductIds()).size()) {
                throw new BadRequestException("One or more tagged products do not exist");
            }
            taggedProducts.addAll(found);
        }

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
                .viewsCount(1L)
                .likesCount(0)
                .commentsCount(0)
                .sharesCount(0)
                .savesCount(0)
                .feedTab(FeedTab.FOR_YOU)
                .hashtags(tags)
                .products(taggedProducts)
                .build();

        Reel saved = reelRepository.save(reel);
        return mapToReelResponse(saved);
    }

    @Override
    public void deleteSeek(String userId, String reelId) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);
        Reel reel = reelRepository.findById(reelId)
                .orElseThrow(() -> new ResourceNotFoundException("Reel", "id", reelId));

        if (!reel.getManufacturer().getId().equals(manufacturer.getId())) {
            throw new ForbiddenException("You cannot delete reels belonging to another factory");
        }

        reelRepository.delete(reel);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RfqResponse> getRfqs(String userId) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);
        return findMatchedRfqs(manufacturer).stream()
                .map(this::mapToRfqResponse)
                .collect(Collectors.toList());
    }

    /**
     * RFQs routed to a factory: those in its categories, or every RFQ if it has no categories.
     * Shared by the RFQ list and the dashboard stats so the two always agree.
     */
    private List<Rfq> findMatchedRfqs(Manufacturer manufacturer) {
        List<String> categoryIds = manufacturer.getCategories().stream()
                .map(Category::getId)
                .collect(Collectors.toList());
        return categoryIds.isEmpty()
                ? rfqRepository.findAllByOrderByCreatedAtDesc()
                : rfqRepository.findByCategoryIdInOrderByCreatedAtDesc(categoryIds);
    }

    @Override
    public void submitQuote(String userId, String rfqId, RfqQuoteRequest request) {
        Manufacturer manufacturer = getOrCreateManufacturer(userId);
        Rfq rfq = rfqRepository.findById(rfqId)
                .orElseThrow(() -> new ResourceNotFoundException("Rfq", "id", rfqId));

        Currency currency = Currency.USD;
        if (request.getCurrency() != null && request.getCurrency().equalsIgnoreCase("INR")) {
            currency = Currency.INR;
        }

        RfqQuote quote = RfqQuote.builder()
                .rfq(rfq)
                .manufacturer(manufacturer)
                .quotePrice(request.getQuotePrice())
                .currency(currency)
                .leadTimeDays(request.getLeadTimeDays())
                .notes(request.getNotes())
                .attachmentUrl(request.getAttachmentUrl())
                .status(QuoteStatus.PENDING)
                .build();

        rfqQuoteRepository.save(quote);
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
        return res;
    }

    private ProductResponse mapToProductResponse(Product p) {
        ProductResponse res = modelMapper.map(p, ProductResponse.class);
        res.setManufacturerId(p.getManufacturer().getId());
        res.setCategoryId(p.getCategory() != null ? p.getCategory().getId() : null);
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