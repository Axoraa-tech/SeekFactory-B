package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Request.rfq.RfqCreateRequest;
import seekfactory.axoraa.dto.Response.rfq.RfqResponse;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.entity.Rfqs.Rfq;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.Currency;
import seekfactory.axoraa.enums.Incoterm;
import seekfactory.axoraa.enums.RfqStatus;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.CategoryRepository;
import seekfactory.axoraa.repository.Rfqs.RfqRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.RfqService;
import seekfactory.axoraa.utils.IdGenerator;

import java.time.Year;
import java.util.List;
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
 * The reference number format: RFQ-{YEAR}-{6-DIGIT-SEQUENCE}
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RfqServiceImpl implements RfqService {

    private final RfqRepository rfqRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public RfqResponse submit(String userId, RfqCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

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

        // Resolve optional category
        Category category = null;
        if (request.getCategoryId() != null && !request.getCategoryId().isBlank()) {
            category = categoryRepository.findById(request.getCategoryId()).orElse(null);
        }

        // Parse currency and incoterm with defaults
        Currency currency = parseCurrency(request.getCurrency());
        Incoterm incoterm = parseIncoterm(request.getIncoterm());

        Rfq rfq = Rfq.builder()
                .referenceNumber(referenceNumber)
                .user(user)
                .productName(request.getProductName())
                .category(category)
                .quantity(request.getQuantity())
                .unit(request.getUnit() != null ? request.getUnit() : "Pieces")
                .targetPrice(request.getTargetPrice())
                .currency(currency)
                .incoterm(incoterm)
                .companyName(request.getCompanyName())
                .details(request.getDetails())
                .attachmentName(request.getAttachmentName())
                .attachmentSize(request.getAttachmentSize())
                .attachmentUrl(request.getAttachmentUrl())
                .status(RfqStatus.SUBMITTED)
                .build();

        Rfq saved = rfqRepository.save(rfq);
        log.info("RFQ submitted: {} by user {}", referenceNumber, userId);

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RfqResponse> listByUser(String userId) {
        return rfqRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─── Private Helpers ──────────────────────────────────────

    private RfqResponse mapToResponse(Rfq rfq) {
        return RfqResponse.builder()
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
                .status(rfq.getStatus().name())
                .createdAt(rfq.getCreatedAt().toString())
                .build();
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