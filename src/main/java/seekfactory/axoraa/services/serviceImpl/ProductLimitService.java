package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Request.plan.ProductLimitDecisionRequest;
import seekfactory.axoraa.dto.Request.plan.ProductLimitUpgradeRequest;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.Page;
import seekfactory.axoraa.dto.Response.plan.AdminProductLimitRequestRow;
import seekfactory.axoraa.dto.Response.plan.ProductLimitRequestResponse;
import seekfactory.axoraa.dto.Response.plan.ProductUsageResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.ProductLimitRequest;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.ProductLimitRequestStatus;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ForbiddenException;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.ProductLimitRequestRepository;
import seekfactory.axoraa.repository.ProductRepository;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-factory product allowance and the manual upgrade-request queue.
 * No pricing or payment is involved: an admin raises the limit by hand.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductLimitService {

    public static final int MAX_LIMIT = 1000;
    private static final int MAX_PAGE_SIZE = 100;

    private final ManufacturerRepository manufacturerRepository;
    private final ProductRepository productRepository;
    private final ProductLimitRequestRepository requestRepository;

    public static int limitOf(Manufacturer m) {
        return m.getProductLimit() != null ? m.getProductLimit() : Manufacturer.DEFAULT_PRODUCT_LIMIT;
    }

    /**
     * Locks the factory row and throws if one more live product would exceed its limit.
     * Called before creating a product so two parallel creates cannot both pass.
     */
    public void requireRoomForProduct(Manufacturer manufacturer) {
        Manufacturer locked = manufacturerRepository.findByIdForUpdate(manufacturer.getId()).orElse(manufacturer);
        int limit = limitOf(locked);
        long count = productRepository.countByManufacturerIdAndIsActiveTrue(locked.getId());
        if (count >= limit) {
            throw new ForbiddenException("Product limit reached: your plan allows " + limit + " product"
                    + (limit == 1 ? "" : "s") + " and you have " + count
                    + ". Upgrade the plan / contact admin to request a higher limit.");
        }
    }

    @Transactional(readOnly = true)
    public ProductUsageResponse usage(Manufacturer manufacturer) {
        int limit = limitOf(manufacturer);
        int count = (int) productRepository.countByManufacturerIdAndIsActiveTrue(manufacturer.getId());
        ProductLimitRequestResponse latest = requestRepository
                .findFirstByManufacturerIdOrderByCreatedAtDesc(manufacturer.getId())
                .map(ProductLimitService::toResponse).orElse(null);
        return new ProductUsageResponse(count, limit, count < limit, latest);
    }

    public ProductLimitRequestResponse requestUpgrade(Manufacturer manufacturer, ProductLimitUpgradeRequest body) {
        Manufacturer locked = manufacturerRepository.findByIdForUpdate(manufacturer.getId()).orElse(manufacturer);
        int limit = limitOf(locked);
        int count = (int) productRepository.countByManufacturerIdAndIsActiveTrue(locked.getId());
        if (body.getRequestedLimit() <= limit) {
            throw new BadRequestException("Requested limit must be higher than your current limit of " + limit);
        }
        if (requestRepository.existsByManufacturerIdAndStatus(locked.getId(), ProductLimitRequestStatus.PENDING)) {
            throw new BadRequestException("You already have a pending request. The admin will get back to you.");
        }
        ProductLimitRequest saved = requestRepository.save(ProductLimitRequest.builder()
                .manufacturer(locked)
                .currentCount(count)
                .currentLimit(limit)
                .requestedLimit(body.getRequestedLimit())
                .message(blankToNull(body.getMessage()))
                .build());
        log.info("Product limit request {} from manufacturer {}: {} -> {}", saved.getId(), locked.getId(), limit,
                body.getRequestedLimit());
        return toResponse(saved);
    }

    /* ─────────────────────────── Admin ─────────────────────────── */

    @Transactional(readOnly = true)
    public Page<AdminProductLimitRequestRow> list(String status, String q, int page, int size) {
        Set<ProductLimitRequestStatus> statuses = parseStatus(status);
        String pattern = "%" + (q == null ? "" : q.trim().toLowerCase(Locale.ROOT)) + "%";
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        var result = requestRepository.search(statuses, pattern, PageRequest.of(Math.max(page, 0), safeSize));
        List<AdminProductLimitRequestRow> rows = result.getContent().stream().map(this::toRow).toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        long all = 0;
        for (ProductLimitRequestStatus s : ProductLimitRequestStatus.values()) {
            long n = requestRepository.countByStatus(s);
            counts.put(s.name(), n);
            all += n;
        }
        counts.put("all_count", all);
        return new Page<>(rows, result.getTotalElements(), result.getNumber(), safeSize, counts);
    }

    public void approve(String requestId, ProductLimitDecisionRequest decision, String adminId) {
        ProductLimitRequest request = pending(requestId);
        int newLimit = decision != null && decision.getApprovedLimit() != null
                ? decision.getApprovedLimit() : request.getRequestedLimit();
        String manufacturerId = request.getManufacturer().getId();
        Manufacturer m = manufacturerRepository.findByIdForUpdate(manufacturerId)
                .orElseThrow(() -> new ResourceNotFoundException("Manufacturer", "id", manufacturerId));
        m.setProductLimit(newLimit);
        manufacturerRepository.save(m);
        close(request, ProductLimitRequestStatus.APPROVED, decision != null ? decision.getNote() : null, adminId);
        log.info("Admin {} approved product limit request {}: manufacturer {} limit -> {}", adminId, requestId,
                m.getId(), newLimit);
    }

    public void reject(String requestId, ProductLimitDecisionRequest decision, String adminId) {
        String note = decision != null ? blankToNull(decision.getNote()) : null;
        if (note == null) throw new BadRequestException("A reason is required to decline a request");
        ProductLimitRequest request = pending(requestId);
        close(request, ProductLimitRequestStatus.REJECTED, note, adminId);
        log.info("Admin {} declined product limit request {}", adminId, requestId);
    }

    /** Direct manual override by an admin. Lowering it never removes or hides existing products. */
    public void setLimit(String manufacturerId, int limit, String adminId) {
        if (limit < 1 || limit > MAX_LIMIT) throw new BadRequestException("Limit must be between 1 and " + MAX_LIMIT);
        Manufacturer m = manufacturerRepository.findByIdForUpdate(manufacturerId)
                .orElseThrow(() -> new ResourceNotFoundException("Manufacturer", "id", manufacturerId));
        m.setProductLimit(limit);
        manufacturerRepository.save(m);
        log.info("Admin {} set product limit of manufacturer {} to {}", adminId, manufacturerId, limit);
    }

    private ProductLimitRequest pending(String requestId) {
        ProductLimitRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Product limit request", "id", requestId));
        if (request.getStatus() != ProductLimitRequestStatus.PENDING) {
            throw new BadRequestException("This request was already " + request.getStatus().name().toLowerCase(Locale.ROOT));
        }
        return request;
    }

    private void close(ProductLimitRequest request, ProductLimitRequestStatus status, String note, String adminId) {
        request.setStatus(status);
        request.setAdminNote(blankToNull(note));
        request.setReviewedBy(adminId);
        request.setReviewedAt(Instant.now());
        requestRepository.save(request);
    }

    private static Set<ProductLimitRequestStatus> parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return EnumSet.allOf(ProductLimitRequestStatus.class);
        }
        try {
            return EnumSet.of(ProductLimitRequestStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown status: " + status);
        }
    }

    private AdminProductLimitRequestRow toRow(ProductLimitRequest r) {
        Manufacturer m = r.getManufacturer();
        User owner = m.getUser();
        int liveCount = (int) productRepository.countByManufacturerIdAndIsActiveTrue(m.getId());
        return new AdminProductLimitRequestRow(r.getId(), m.getId(), m.getName(), m.getSlug(),
                owner != null ? owner.getName() : null, owner != null ? owner.getEmail() : null,
                liveCount, limitOf(m), r.getCurrentCount(), r.getCurrentLimit(), r.getRequestedLimit(),
                r.getMessage(), r.getStatus().name(), r.getAdminNote(), r.getReviewedBy(), r.getReviewedAt(),
                r.getCreatedAt());
    }

    static ProductLimitRequestResponse toResponse(ProductLimitRequest r) {
        return new ProductLimitRequestResponse(r.getId(), r.getCurrentCount(), r.getCurrentLimit(),
                r.getRequestedLimit(), r.getMessage(), r.getStatus().name(), r.getAdminNote(),
                r.getCreatedAt(), r.getReviewedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
