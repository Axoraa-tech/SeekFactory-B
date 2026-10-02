package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Request.order.OrderContactRequest;
import seekfactory.axoraa.dto.Request.rfq.RfqCreateRequest;
import seekfactory.axoraa.dto.Response.order.OrderResponse;
import seekfactory.axoraa.dto.Response.rfq.RfqQuoteResponse;
import seekfactory.axoraa.dto.Response.rfq.RfqResponse;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.entity.OrderRequest;
import seekfactory.axoraa.entity.Rfqs.Rfq;
import seekfactory.axoraa.entity.Rfqs.RfqQuote;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.Currency;
import seekfactory.axoraa.enums.Incoterm;
import seekfactory.axoraa.enums.NotificationType;
import seekfactory.axoraa.enums.QuoteStatus;
import seekfactory.axoraa.enums.RfqStatus;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.mapper.CatalogMapper;
import seekfactory.axoraa.repository.CategoryRepository;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.repository.OrderRequestRepository;
import seekfactory.axoraa.repository.Rfqs.RfqQuoteRepository;
import seekfactory.axoraa.repository.Rfqs.RfqRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.NotificationService;
import seekfactory.axoraa.services.services.OrderService;
import seekfactory.axoraa.services.services.RfqService;
import seekfactory.axoraa.utils.IdGenerator;
import seekfactory.axoraa.utils.InputUtils;

import java.time.Year;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages Request for Quotation lifecycle.
 *
 * When a buyer submits an RFQ, the system:
 * 1. Generates a unique reference number (e.g., "RFQ-2026-000142")
 * 2. Associates it with the buyer's user account
 * 3. Sets initial status to SUBMITTED
 * 4. Returns the created RFQ with its tracking reference
 *
 * Factories then quote on it (FactoryService); the buyer reviews the quotes and
 * accepts one, which places an order, or rejects them.
 *
 * The reference number format: RFQ-{YEAR}-{6-DIGIT-SEQUENCE}
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RfqServiceImpl implements RfqService {

    /** An RFQ still open for quotes and decisions. */
    private static final Set<RfqStatus> OPEN_STATUSES =
            EnumSet.of(RfqStatus.SUBMITTED, RfqStatus.REVIEWING, RfqStatus.QUOTING, RfqStatus.QUOTED);

    private final RfqRepository rfqRepository;
    private final RfqQuoteRepository rfqQuoteRepository;
    private final OrderRequestRepository orderRequestRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final ProductRepository productRepository;
    private final ReelRepository reelRepository;
    private final OrderService orderService;
    private final NotificationService notificationService;
    private final CatalogMapper catalogMapper;

    @Override
    public RfqResponse submit(String userId, RfqCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!InputUtils.isSafeUrl(request.getAttachmentUrl())) {
            throw new BadRequestException("Attachment must be an uploaded file or an http(s) link");
        }

        // Generate unique reference number
        int currentYear = Year.now().getValue();
        String yearPrefix = "RFQ-" + currentYear + "-%";
        int nextSequence;
        try {
            nextSequence = rfqRepository.findMaxSequenceForYear(yearPrefix) + 1;
        } catch (Exception e) {
            nextSequence = 1;
        }
        String referenceNumber = IdGenerator.generateRfqReference(currentYear, nextSequence);

        // Optional target: one factory, one of its products, the seek it was sent from
        Product product = resolveProduct(request.getProductId());
        Manufacturer manufacturer = resolveManufacturer(request.getManufacturerId(), product);
        Reel sourceReel = resolveReel(request.getReelId(), manufacturer);

        // Resolve optional category; a chosen product decides it (and the product name)
        Category category = null;
        if (product != null) {
            category = product.getCategory();
        } else if (request.getCategoryId() != null && !request.getCategoryId().isBlank()) {
            category = categoryRepository.findById(request.getCategoryId()).orElse(null);
        }
        String productName = product != null ? product.getName() : request.getProductName();

        Rfq rfq = Rfq.builder()
                .referenceNumber(referenceNumber)
                .user(user)
                .productName(productName)
                .category(category)
                .manufacturer(manufacturer)
                .product(product)
                .sourceReel(sourceReel)
                .quantity(request.getQuantity())
                .unit(request.getUnit() != null ? request.getUnit() : "Pieces")
                .targetPrice(request.getTargetPrice())
                .currency(parseCurrency(request.getCurrency()))
                .incoterm(parseIncoterm(request.getIncoterm()))
                .companyName(request.getCompanyName())
                .details(request.getDetails())
                .attachmentName(request.getAttachmentName())
                .attachmentSize(request.getAttachmentSize())
                .attachmentUrl(request.getAttachmentUrl())
                .status(RfqStatus.SUBMITTED)
                .build();

        Rfq saved = rfqRepository.save(rfq);
        log.info("RFQ submitted: {} by user {}{}", referenceNumber, userId,
                manufacturer != null ? " to manufacturer " + manufacturer.getId() : "");

        // A directed RFQ is meant for this one factory: tell it right away
        if (manufacturer != null && manufacturer.getUser() != null) {
            notificationService.notify(manufacturer.getUser(), NotificationType.RFQ,
                    "New RFQ for " + productName,
                    request.getCompanyName() + " requested a quote for " + request.getQuantity() + " "
                            + saved.getUnit() + " (" + referenceNumber + ")",
                    saved.getId());
        }

        return mapToResponse(saved, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RfqResponse> listByUser(String userId) {
        return rfqRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(r -> mapToResponse(r, false))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public RfqResponse getMine(String userId, String rfqId) {
        return mapToResponse(findMine(userId, rfqId), true);
    }

    @Override
    public RfqResponse cancel(String userId, String rfqId) {
        Rfq rfq = findMine(userId, rfqId);
        requireOpen(rfq);
        rfq.setStatus(RfqStatus.CANCELLED);
        for (RfqQuote quote : rfq.getQuotes()) {
            if (quote.getStatus() == QuoteStatus.PENDING) {
                quote.setStatus(QuoteStatus.REJECTED);
                notificationService.notify(quote.getManufacturer().getUser(), NotificationType.RFQ,
                        rfq.getReferenceNumber() + " was cancelled",
                        "The buyer cancelled \"" + rfq.getProductName() + "\", so your quote is closed.",
                        rfq.getId());
            }
        }
        return mapToResponse(rfqRepository.save(rfq), true);
    }

    @Override
    public RfqResponse acceptQuote(String userId, String rfqId, String quoteId, OrderContactRequest contact) {
        Rfq rfq = findMine(userId, rfqId);
        requireOpen(rfq);
        RfqQuote accepted = findPendingQuote(rfq, quoteId);

        accepted.setStatus(QuoteStatus.ACCEPTED);
        for (RfqQuote other : rfq.getQuotes()) {
            if (other != accepted && other.getStatus() == QuoteStatus.PENDING) {
                other.setStatus(QuoteStatus.REJECTED);
                notificationService.notify(other.getManufacturer().getUser(), NotificationType.QUOTE,
                        "Quote not selected for " + rfq.getReferenceNumber(),
                        "The buyer chose another factory for \"" + rfq.getProductName() + "\".",
                        rfq.getId());
            }
        }
        rfq.setStatus(RfqStatus.ACCEPTED);
        rfqRepository.save(rfq);

        OrderResponse order = orderService.createFromQuote(accepted, contact);
        notificationService.notify(accepted.getManufacturer().getUser(), NotificationType.QUOTE,
                "Quote accepted for " + rfq.getReferenceNumber(),
                "Your quote for \"" + rfq.getProductName() + "\" was accepted. Order " + order.getReferenceNumber() + " is placed.",
                order.getId());
        log.info("RFQ {} quote {} accepted; order {}", rfq.getReferenceNumber(), quoteId, order.getReferenceNumber());
        return mapToResponse(rfq, true);
    }

    @Override
    public RfqResponse rejectQuote(String userId, String rfqId, String quoteId) {
        Rfq rfq = findMine(userId, rfqId);
        requireOpen(rfq);
        RfqQuote quote = findPendingQuote(rfq, quoteId);
        quote.setStatus(QuoteStatus.REJECTED);
        rfqQuoteRepository.save(quote);
        notificationService.notify(quote.getManufacturer().getUser(), NotificationType.QUOTE,
                "Quote declined for " + rfq.getReferenceNumber(),
                "The buyer declined your quote for \"" + rfq.getProductName() + "\".",
                rfq.getId());
        return mapToResponse(rfq, true);
    }

    // ─── Private Helpers ──────────────────────────────────────

    /** A product the buyer can see (active, listed, from a verified factory), or null if none was chosen. */
    private Product resolveProduct(String productId) {
        if (productId == null || productId.isBlank()) return null;
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
        if (!product.isPubliclyVisible() || !Boolean.TRUE.equals(product.getManufacturer().getVerified())) {
            throw new ResourceNotFoundException("Product", "id", productId);
        }
        return product;
    }

    /** The factory the RFQ is for: the chosen product's factory, which must match manufacturerId if both are sent. */
    private Manufacturer resolveManufacturer(String manufacturerId, Product product) {
        boolean hasId = manufacturerId != null && !manufacturerId.isBlank();
        if (product != null) {
            Manufacturer owner = product.getManufacturer();
            if (hasId && !owner.getId().equals(manufacturerId)) {
                throw new BadRequestException("This product belongs to a different manufacturer");
            }
            return owner;
        }
        if (!hasId) return null;
        Manufacturer manufacturer = manufacturerRepository.findById(manufacturerId)
                .filter(m -> Boolean.TRUE.equals(m.getVerified()))
                .orElseThrow(() -> new ResourceNotFoundException("Manufacturer", "id", manufacturerId));
        return manufacturer;
    }

    /** The seek the RFQ came from; it must belong to the RFQ's factory. */
    private Reel resolveReel(String reelId, Manufacturer manufacturer) {
        if (reelId == null || reelId.isBlank()) return null;
        Reel reel = reelRepository.findById(reelId)
                .orElseThrow(() -> new ResourceNotFoundException("Reel", "id", reelId));
        if (manufacturer == null || !reel.getManufacturer().getId().equals(manufacturer.getId())) {
            throw new BadRequestException("This seek belongs to a different manufacturer");
        }
        return reel;
    }

    private Rfq findMine(String userId, String rfqId) {
        return rfqRepository.findByIdAndUserId(rfqId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Rfq", "id", rfqId));
    }

    private static void requireOpen(Rfq rfq) {
        if (!OPEN_STATUSES.contains(rfq.getStatus())) {
            throw new BadRequestException("This RFQ is already " + rfq.getStatus().name().toLowerCase().replace('_', ' '));
        }
    }

    private static RfqQuote findPendingQuote(Rfq rfq, String quoteId) {
        RfqQuote quote = rfq.getQuotes().stream()
                .filter(q -> q.getId().equals(quoteId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Quote", "id", quoteId));
        if (quote.getStatus() != QuoteStatus.PENDING) {
            throw new BadRequestException("This quote is already " + quote.getStatus().name().toLowerCase());
        }
        return quote;
    }

    private RfqResponse mapToResponse(Rfq rfq, boolean withQuotes) {
        RfqResponse response = RfqResponse.builder()
                .id(rfq.getId())
                .referenceNumber(rfq.getReferenceNumber())
                .productName(rfq.getProductName())
                .categoryId(rfq.getCategory() != null ? rfq.getCategory().getId() : null)
                .quantity(rfq.getQuantity())
                .unit(rfq.getUnit())
                .targetPrice(rfq.getTargetPrice())
                .currency(rfq.getCurrency().name())
                .incoterm(rfq.getIncoterm().name())
                .companyName(rfq.getCompanyName())
                .details(rfq.getDetails())
                .attachmentName(rfq.getAttachmentName())
                .attachmentSize(rfq.getAttachmentSize())
                .attachmentUrl(rfq.getAttachmentUrl())
                .status(rfq.getStatus().name())
                .createdAt(rfq.getCreatedAt().toString())
                .quoteCount(rfq.getQuotes().size())
                .build();
        RfqTargets.apply(response, rfq);

        if (withQuotes) {
            response.setQuotes(rfqQuoteRepository.findByRfqIdOrderByCreatedAtDesc(rfq.getId()).stream()
                    .map(q -> RfqQuoteResponse.builder()
                            .id(q.getId())
                            .manufacturer(catalogMapper.toManufacturer(q.getManufacturer()))
                            .quotePrice(q.getQuotePrice())
                            .currency(q.getCurrency().name())
                            .leadTimeDays(q.getLeadTimeDays())
                            .notes(q.getNotes())
                            .attachmentUrl(q.getAttachmentUrl())
                            .status(q.getStatus().name())
                            .createdAt(q.getCreatedAt() != null ? q.getCreatedAt().toString() : null)
                            .orderId(q.getStatus() == QuoteStatus.ACCEPTED
                                    ? orderRequestRepository.findByRfqQuoteId(q.getId()).map(OrderRequest::getId).orElse(null)
                                    : null)
                            .build())
                    .collect(Collectors.toList()));
        }
        return response;
    }

    private Currency parseCurrency(String value) {
        if (value == null || value.isBlank()) return Currency.INR;
        try {
            return Currency.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Currency.INR;
        }
    }

    private Incoterm parseIncoterm(String value) {
        if (value == null || value.isBlank()) return Incoterm.FOB;
        try {
            return Incoterm.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Incoterm.FOB;
        }
    }
}
