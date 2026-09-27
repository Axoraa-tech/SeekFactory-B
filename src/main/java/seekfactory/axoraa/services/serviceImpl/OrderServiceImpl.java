package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.NotificationRepository;
import seekfactory.axoraa.repository.OrderRequestRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.OrderService;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceImpl implements OrderService {

    /** A repeat click within this window returns the existing pending order instead of a duplicate. */
    static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(2);

    private static final String REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRequestRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final NotificationRepository notificationRepository;

    @Override
    public OrderResponse placeOrder(String buyerUserId, OrderCreateRequest request) {
        User buyer = userRepository.findById(buyerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", buyerUserId));
        Product product = productRepository.findBySlug(request.getProductSlug().trim())
                .filter(Product::isPubliclyVisible)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "slug", request.getProductSlug()));
        Manufacturer manufacturer = product.getManufacturer();

        if (manufacturer.getUser() != null && buyerUserId.equals(manufacturer.getUser().getId())) {
            throw new BadRequestException("You cannot place an order for your own product");
        }

        var recent = orderRepository.findFirstByBuyerIdAndProductIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                buyerUserId, product.getId(), OrderStatus.PENDING, Instant.now().minus(DUPLICATE_WINDOW));
        if (recent.isPresent()) {
            return toResponse(recent.get(), false);
        }

        OrderRequest order = OrderRequest.builder()
                .referenceNumber(newReferenceNumber())
                .product(product)
                .manufacturer(manufacturer)
                .buyer(buyer)
                .productName(product.getName())
                .productSlug(product.getSlug())
                .productImageUrl(product.getImageUrl())
                .unitPriceInr(product.getPriceInr())
                .unit(product.getUnit())
                .quantity(request.getQuantity())
                .buyerNote(blankToNull(request.getNote()))
                .build();
        OrderRequest saved = orderRepository.save(order);

        if (manufacturer.getUser() != null) {
            String who = buyer.getCompanyName() != null && !buyer.getCompanyName().isBlank()
                    ? buyer.getCompanyName() : buyer.getName();
            notify(manufacturer.getUser(), "New order request " + saved.getReferenceNumber(),
                    who + " requested " + saved.getQuantity() + " × " + saved.getProductName() + ".",
                    saved.getId());
        }
        log.info("Order request {} placed by user {} for product {}", saved.getReferenceNumber(), buyerUserId, product.getId());
        return toResponse(saved, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> listBuyerOrders(String buyerUserId) {
        return orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerUserId).stream()
                .map(o -> toResponse(o, false))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> listFactoryOrders(String supplierUserId) {
        return manufacturerRepository.findByUserId(supplierUserId)
                .map(m -> orderRepository.findByManufacturerIdOrderByCreatedAtDesc(m.getId()).stream()
                        .map(o -> toResponse(o, true))
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> listOrdersBetween(String buyerUserId, String manufacturerId, boolean forSeller) {
        return orderRepository.findByBuyerIdAndManufacturerIdOrderByCreatedAtDesc(buyerUserId, manufacturerId).stream()
                .map(o -> toResponse(o, forSeller))
                .collect(Collectors.toList());
    }

    @Override
    public OrderResponse updateStatus(String supplierUserId, String orderId, OrderStatusUpdateRequest request) {
        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(request.getStatus().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown order status: " + request.getStatus());
        }

        OrderRequest order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        Manufacturer manufacturer = order.getManufacturer();
        if (manufacturer.getUser() == null || !supplierUserId.equals(manufacturer.getUser().getId())) {
            throw new ForbiddenException("This order belongs to another factory");
        }

        String note = blankToNull(request.getNote());
        boolean statusChanged = order.getStatus() != newStatus;
        boolean noteChanged = !Objects.equals(order.getSellerNote(), note);
        if (!statusChanged && !noteChanged) {
            return toResponse(order, true);
        }

        order.setStatus(newStatus);
        order.setSellerNote(note);
        if (statusChanged) {
            order.setStatusUpdatedAt(Instant.now());
        }
        OrderRequest saved = orderRepository.save(order);

        String label = newStatus.name().charAt(0) + newStatus.name().substring(1).toLowerCase(Locale.ROOT);
        notify(order.getBuyer(),
                "Order " + saved.getReferenceNumber() + (statusChanged ? " is now " + label : " updated"),
                manufacturer.getName() + " updated your order for " + saved.getProductName()
                        + (note != null ? ": " + note : "."),
                saved.getId());
        return toResponse(saved, true);
    }

    // ─── Helpers ──────────────────────────────────────────────

    private void notify(User user, String title, String body, String referenceId) {
        notificationRepository.save(Notification.builder()
                .user(user)
                .title(title)
                .body(body)
                .notificationType(NotificationType.ORDER)
                .referenceId(referenceId)
                .build());
    }

    private String newReferenceNumber() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                code.append(REF_ALPHABET.charAt(RANDOM.nextInt(REF_ALPHABET.length())));
            }
            String reference = "ORD-" + Year.now().getValue() + "-" + code;
            if (!orderRepository.existsByReferenceNumber(reference)) {
                return reference;
            }
        }
        throw new IllegalStateException("Could not allocate an order reference");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** @param forSeller include the buyer's contact details (the seller needs them to follow up). */
    private OrderResponse toResponse(OrderRequest o, boolean forSeller) {
        Manufacturer m = o.getManufacturer();
        User b = o.getBuyer();
        BigDecimal total = o.getUnitPriceInr() == null ? null
                : o.getUnitPriceInr().multiply(BigDecimal.valueOf(o.getQuantity()));

        OrderResponse.Party buyer = OrderResponse.Party.builder()
                .id(b.getId())
                .name(b.getName())
                .companyName(b.getCompanyName())
                .country(b.getCountry())
                .avatarUrl(b.getAvatarUrl())
                .email(forSeller ? b.getEmail() : null)
                .phone(forSeller ? b.getPhone() : null)
                .build();

        return OrderResponse.builder()
                .id(o.getId())
                .referenceNumber(o.getReferenceNumber())
                .status(o.getStatus().name())
                .statusUpdatedAt(o.getStatusUpdatedAt() != null ? o.getStatusUpdatedAt().toString() : null)
                .createdAt(o.getCreatedAt() != null ? o.getCreatedAt().toString() : null)
                .productId(o.getProduct() != null ? o.getProduct().getId() : null)
                .productSlug(o.getProductSlug())
                .productName(o.getProductName())
                .productImageUrl(o.getProductImageUrl())
                .unitPriceInr(o.getUnitPriceInr())
                .unit(o.getUnit())
                .quantity(o.getQuantity())
                .estimatedTotalInr(total)
                .buyerNote(o.getBuyerNote())
                .sellerNote(o.getSellerNote())
                .manufacturer(OrderResponse.Party.builder()
                        .id(m.getId())
                        .name(m.getName())
                        .slug(m.getSlug())
                        .country(m.getCountry())
                        .avatarUrl(m.getLogoUrl())
                        .build())
                .buyer(buyer)
                .build();
    }
}
