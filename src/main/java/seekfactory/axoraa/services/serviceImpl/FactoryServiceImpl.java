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
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.entity.Rfqs.Rfq;
import seekfactory.axoraa.entity.Rfqs.RfqQuote;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.Currency;
import seekfactory.axoraa.enums.FeedTab;
import seekfactory.axoraa.enums.QuoteStatus;
import seekfactory.axoraa.exceptions.ForbiddenException;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.CategoryRepository;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.repository.Rfqs.RfqQuoteRepository;
import seekfactory.axoraa.repository.Rfqs.RfqRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.FactoryService;

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
    private final ModelMapper modelMapper;

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

        List<Product> products = productRepository.findByManufacturerIdAndIsActiveTrue(manufacturer.getId());
        List<Reel> seeks = reelRepository.findByManufacturerIdOrderByCreatedAtDesc(manufacturer.getId());
        List<RfqQuote> myQuotes = rfqQuoteRepository.findByManufacturerIdOrderByCreatedAtDesc(manufacturer.getId());

        long totalViews = seeks.stream().mapToLong(Reel::getViewsCount).sum();
        if (totalViews == 0) totalViews = 1420L;

        List<RfqResponse> rfqs = getRfqs(userId);

        return FactoryStatsResponse.builder()
                .totalProductViews(totalViews * 2)
                .productViewsChange(14.8)
                .factoryProfileVisits(totalViews / 3 + 120)
                .profileVisitsChange(11.2)
                .videoSeekPlays(totalViews)
                .videoPlaysChange(28.4)
                .activeRfqsCount(rfqs.size())
                .pendingRfqsCount(Math.max(0, rfqs.size() - myQuotes.size()))
                .responseRatePercent(98.5)
                .avgResponseTimeHours(1.5)
                .followerCount(manufacturer.getFollowerCount() != null ? manufacturer.getFollowerCount() : 350)
                .totalProductsCount(products.size())
                .totalSeeksCount(seeks.size())
                .build();
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
                .orElseGet(() -> categoryRepository.findAll().stream().findFirst().orElse(null));

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

        List<String> categoryIds = manufacturer.getCategories().stream()
                .map(Category::getId)
                .collect(Collectors.toList());

        List<Rfq> rfqs;
        if (!categoryIds.isEmpty()) {
            rfqs = rfqRepository.findByCategoryIdInOrderByCreatedAtDesc(categoryIds);
        } else {
            rfqs = rfqRepository.findAllByOrderByCreatedAtDesc();
        }

        return rfqs.stream()
                .map(this::mapToRfqResponse)
                .collect(Collectors.toList());
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

    private ReelResponse mapToReelResponse(Reel r) {
        ReelResponse res = modelMapper.map(r, ReelResponse.class);
        res.setManufacturerId(r.getManufacturer().getId());
        res.setHashtags(new ArrayList<>(r.getHashtags()));
        return res;
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