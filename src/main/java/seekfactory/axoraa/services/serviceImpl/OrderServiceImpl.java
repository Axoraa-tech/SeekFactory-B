package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Request.order.CartCheckoutRequest;
import seekfactory.axoraa.dto.Request.order.CartItemRequest;
import seekfactory.axoraa.dto.Request.order.OrderContactRequest;
import seekfactory.axoraa.dto.Request.order.OrderCreateRequest;
import seekfactory.axoraa.dto.Request.order.OrderStatusUpdateRequest;
import seekfactory.axoraa.dto.Response.order.CartItemResponse;
import seekfactory.axoraa.dto.Response.order.CartResponse;
import seekfactory.axoraa.dto.Response.order.OrderResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Notification;
import seekfactory.axoraa.entity.OrderRequest;
import seekfactory.axoraa.entity.Orders.CartItem;
import seekfactory.axoraa.entity.PriceTier;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.entity.Rfqs.Rfq;
import seekfactory.axoraa.entity.Rfqs.RfqQuote;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.Currency;
import seekfactory.axoraa.enums.NotificationType;
import seekfactory.axoraa.enums.OrderSource;
import seekfactory.axoraa.enums.OrderStatus;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ForbiddenException;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.mapper.CatalogMapper;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.NotificationRepository;
import seekfactory.axoraa.repository.OrderRequestRepository;
import seekfactory.axoraa.repository.Orders.CartItemRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.OrderService;
import seekfactory.axoraa.utils.InputUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    /** The buyer may withdraw a request until the deal is confirmed. */
    private static final Set<OrderStatus> BUYER_CANCELLABLE =
            EnumSet.of(OrderStatus.PENDING, OrderStatus.CONTACTED, OrderStatus.NEGOTIATING);

    /** Closed deals: their status can no longer change (the seller may still edit the note). */
    private static final Set<OrderStatus> FINAL_STATUSES = EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED);

    private final OrderRequestRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final NotificationRepository notificationRepository;
    private final CartItemRepository cartItemRepository;
    private final CatalogMapper catalogMapper;

    @Override
    public OrderResponse placeOrder(String buyerUserId, OrderCreateRequest request) {
        User buyer = findUser(buyerUserId);
        Product product = productRepository.findBySlug(request.getProductSlug().trim())
                .filter(Product::isPubliclyVisible)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "slug", request.getProductSlug()));
        requireOrderable(product, buyerUserId, request.getQuantity());

        var recent = orderRepository.findFirstByBuyerIdAndProductIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                buyerUserId, product.getId(), OrderStatus.PENDING, Instant.now().minus(DUPLICATE_WINDOW));
        // A double click repeats the same request; a different quantity or a cart order is a new request
        if (recent.isPresent() && recent.get().getSource() == OrderSource.DIRECT
                && recent.get().getQuantity().equals(request.getQuantity())) {
            return toResponse(recent.get(), false);
        }

        OrderRequest saved = orderRepository.save(newProductOrder(buyer, product, request.getQuantity(),
                OrderSource.DIRECT, request.getNote(), request.getContactName(), request.getContactPhone(),
                request.getDeliveryAddress()));
        notifyFactoryOfNewOrder(saved);
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
        if (statusChanged && FINAL_STATUSES.contains(order.getStatus())) {
            throw new BadRequestException("This order is already " + label(order.getStatus()).toLowerCase(Locale.ROOT)
                    + " and its status can no longer change");
        }

        order.setStatus(newStatus);
        order.setSellerNote(note);
        if (statusChanged) {
            order.setStatusUpdatedAt(Instant.now());
        }
        OrderRequest saved = orderRepository.save(order);

        notify(order.getBuyer(),
                "Order " + saved.getReferenceNumber() + (statusChanged ? " is now " + label(newStatus) : " updated"),
                manufacturer.getName() + " updated your order for " + saved.getProductName()
                        + (note != null ? ": " + note : "."),
                saved.getId());
        return toResponse(saved, true);
    }

    @Override
    public OrderResponse cancelByBuyer(String buyerUserId, String orderId, String reason) {
        OrderRequest order = orderRepository.findByIdAndBuyerId(orderId, buyerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        if (!BUYER_CANCELLABLE.contains(order.getStatus())) {
            throw new BadRequestException("This order is " + label(order.getStatus()).toLowerCase(Locale.ROOT)
                    + " and can no longer be cancelled. Please message the factory.");
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(blankToNull(reason));
        order.setStatusUpdatedAt(Instant.now());
        OrderRequest saved = orderRepository.save(order);

        User factoryUser = order.getManufacturer().getUser();
        if (factoryUser != null) {
            notify(factoryUser, "Order " + saved.getReferenceNumber() + " cancelled by the buyer",
                    buyerLabel(order.getBuyer()) + " cancelled the request for " + saved.getProductName()
                            + (saved.getCancelReason() != null ? ": " + saved.getCancelReason() : "."),
                    saved.getId());
        }
        return toResponse(saved, false);
    }

    @Override
    public OrderResponse createFromQuote(RfqQuote quote, OrderContactRequest contact) {
        if (orderRepository.existsByRfqQuoteId(quote.getId())) {
            throw new BadRequestException("An order already exists for this quote");
        }
        Rfq rfq = quote.getRfq();
        int quantity = InputUtils.leadingInt(rfq.getQuantity(), 1);
        Currency currency = quote.getCurrency() != null ? quote.getCurrency() : Currency.INR;
        // Factories quote one total for the whole RFQ quantity; a per-unit INR price only exists for INR quotes
        BigDecimal unitPriceInr = currency == Currency.INR && quote.getQuotePrice() != null
                ? quote.getQuotePrice().divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP)
                : null;

        OrderRequest order = OrderRequest.builder()
                .referenceNumber(newReferenceNumber())
                .manufacturer(quote.getManufacturer())
                .buyer(rfq.getUser())
                .productName(rfq.getProductName())
                .unitPriceInr(unitPriceInr)
                .unit(rfq.getUnit())
                .quantity(quantity)
                .buyerNote(blankToNull(contact.getNote()))
                .source(OrderSource.RFQ_QUOTE)
                .rfqQuote(quote)
                .currency(currency)
                .quotedTotal(quote.getQuotePrice())
                .contactName(contact.getContactName().trim())
                .contactPhone(contact.getContactPhone().trim())
                .deliveryAddress(contact.getDeliveryAddress().trim())
                .build();
        OrderRequest saved = orderRepository.save(order);
        notifyFactoryOfNewOrder(saved);
        return toResponse(saved, false);
    }

    // ─── Cart ─────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(String userId) {
        return buildCart(userId);
    }

    @Override
    public CartResponse addToCart(String userId, CartItemRequest request) {
        User user = findUser(userId);
        Product product = productRepository.findById(request.getProductId())
                .filter(Product::isPubliclyVisible)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId()));
        Optional<CartItem> existing = cartItemRepository.findByUserIdAndProductId(userId, product.getId());
        int quantity = existing.map(CartItem::getQuantity).orElse(0) + request.getQuantity();
        requireOrderable(product, userId, quantity);

        CartItem item = existing.orElseGet(() -> CartItem.builder().user(user).product(product).build());
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return buildCart(userId);
    }

    @Override
    public CartResponse updateCartQuantity(String userId, String cartItemId, int quantity) {
        CartItem item = cartItemRepository.findByIdAndUserId(cartItemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", cartItemId));
        requireMinimum(item.getProduct(), quantity);
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return buildCart(userId);
    }

    @Override
    public CartResponse removeFromCart(String userId, String cartItemId) {
        CartItem item = cartItemRepository.findByIdAndUserId(cartItemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", "id", cartItemId));
        cartItemRepository.delete(item);
        cartItemRepository.flush();
        return buildCart(userId);
    }

    @Override
    public List<OrderResponse> checkout(String userId, CartCheckoutRequest request) {
        User buyer = findUser(userId);
        List<CartItem> cart = cartItemRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (cart.isEmpty()) {
            throw new BadRequestException("Your cart is empty");
        }

        // Which lines to send, and each one's note: the selected lines, or every line with the shared note
        List<SelectedLine> selected = new ArrayList<>();
        if (request.getItems() == null) {
            cart.forEach(item -> selected.add(new SelectedLine(item, request.getNote())));
        } else {
            Map<String, CartItem> byId = new HashMap<>();
            cart.forEach(item -> byId.put(item.getId(), item));
            for (CartCheckoutRequest.Line line : request.getItems()) {
                CartItem item = byId.remove(line.getCartItemId());
                if (item == null) {
                    // Unknown, already sent, someone else's, or listed twice
                    throw new BadRequestException("Some selected items are no longer in your cart. Refresh and try again.");
                }
                selected.add(new SelectedLine(item,
                        line.getNote() != null && !line.getNote().isBlank() ? line.getNote() : request.getNote()));
            }
        }

        // Validate every line before creating anything
        for (SelectedLine line : selected) {
            CartItem item = line.item();
            Product product = item.getProduct();
            if (!product.isPubliclyVisible()) {
                throw new BadRequestException("\"" + product.getName() + "\" is no longer available. Remove it from your cart to continue.");
            }
            requireOrderable(product, userId, item.getQuantity());
        }

        List<OrderResponse> placed = new ArrayList<>();
        for (SelectedLine line : selected) {
            CartItem item = line.item();
            OrderRequest saved = orderRepository.save(newProductOrder(buyer, item.getProduct(), item.getQuantity(),
                    OrderSource.CART, line.note(), request.getContactName(), request.getContactPhone(),
                    request.getDeliveryAddress()));
            notifyFactoryOfNewOrder(saved);
            placed.add(toResponse(saved, false));
        }
        cartItemRepository.deleteAll(selected.stream().map(SelectedLine::item).toList());
        log.info("User {} checked out {} of {} cart line(s)", userId, placed.size(), cart.size());
        return placed;
    }

    // ─── Helpers ──────────────────────────────────────────────

    /** A cart line chosen at checkout and the note sent with it. */
    private record SelectedLine(CartItem item, String note) {}

    private OrderRequest newProductOrder(User buyer, Product product, int quantity, OrderSource source, String note,
                                         String contactName, String contactPhone, String deliveryAddress) {
        return OrderRequest.builder()
                .referenceNumber(newReferenceNumber())
                .product(product)
                .manufacturer(product.getManufacturer())
                .buyer(buyer)
                .productName(product.getName())
                .productSlug(product.getSlug())
                .productImageUrl(product.getImageUrl())
                .unitPriceInr(unitPriceFor(product, quantity))
                .unit(product.getUnit())
                .quantity(quantity)
                .buyerNote(blankToNull(note))
                .source(source)
                .contactName(blankToNull(contactName))
                .contactPhone(blankToNull(contactPhone))
                .deliveryAddress(blankToNull(deliveryAddress))
                .build();
    }

    private CartResponse buildCart(String userId) {
        List<CartItemResponse> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : cartItemRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            Product product = item.getProduct();
            BigDecimal unitPrice = unitPriceFor(product, item.getQuantity());
            BigDecimal lineTotal = unitPrice == null ? null : unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            items.add(CartItemResponse.builder()
                    .id(item.getId())
                    .product(catalogMapper.toProduct(product))
                    .manufacturer(catalogMapper.toManufacturer(product.getManufacturer()))
                    .quantity(item.getQuantity())
                    .minQuantity(minimumQuantity(product))
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .build());
            // Unpriced lines are negotiated with the factory; paused or removed ones cannot be ordered
            if (lineTotal != null && product.isPubliclyVisible()) {
                total = total.add(lineTotal);
            }
        }
        return CartResponse.builder()
                .items(items)
                .itemCount(items.size())
                .totalAmount(total)
                .currency("INR")
                .build();
    }

    private void notifyFactoryOfNewOrder(OrderRequest order) {
        User factoryUser = order.getManufacturer().getUser();
        if (factoryUser == null) return;
        String what = order.getSource() == OrderSource.RFQ_QUOTE
                ? " accepted your quote for " + order.getProductName() + " (" + order.getCurrency() + " "
                        + order.getQuotedTotal().toPlainString() + ")."
                : " requested " + order.getQuantity() + " × " + order.getProductName() + ".";
        notify(factoryUser, "New order request " + order.getReferenceNumber(), buyerLabel(order.getBuyer()) + what,
                order.getId());
    }

    /** Throws unless the buyer may order this product in this quantity. */
    private static void requireOrderable(Product product, String buyerUserId, int quantity) {
        Manufacturer manufacturer = product.getManufacturer();
        if (manufacturer.getUser() != null && buyerUserId.equals(manufacturer.getUser().getId())) {
            throw new BadRequestException("You cannot place an order for your own product");
        }
        requireMinimum(product, quantity);
    }

    private static int minimumQuantity(Product product) {
        return InputUtils.leadingInt(product.getMoq(), 1);
    }

    private static void requireMinimum(Product product, int quantity) {
        int minimum = minimumQuantity(product);
        if (quantity < minimum) {
            throw new BadRequestException("Minimum order for \"" + product.getName() + "\" is " + minimum
                    + (product.getUnit() != null ? " " + product.getUnit() : ""));
        }
    }

    /**
     * Listing price for this quantity: the highest bulk tier the quantity reaches, else the base price.
     * Null when the product has no price (the price is then negotiated with the factory).
     */
    static BigDecimal unitPriceFor(Product product, int quantity) {
        BigDecimal price = product.getPriceInr();
        int bestMin = 0;
        if (product.getPriceTiers() != null) {
            for (PriceTier tier : product.getPriceTiers()) {
                if (tier.getMinQty() != null && tier.getPriceInr() != null
                        && tier.getMinQty() <= quantity && tier.getMinQty() >= bestMin) {
                    bestMin = tier.getMinQty();
                    price = tier.getPriceInr();
                }
            }
        }
        return price == null ? null : price.setScale(2, RoundingMode.HALF_UP);
    }

    private void notify(User user, String title, String body, String referenceId) {
        notificationRepository.save(Notification.builder()
                .user(user)
                .title(title)
                .body(body)
                .notificationType(NotificationType.ORDER)
                .referenceId(referenceId)
                .build());
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
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

    private static String buyerLabel(User buyer) {
        return buyer.getCompanyName() != null && !buyer.getCompanyName().isBlank()
                ? buyer.getCompanyName() : buyer.getName();
    }

    private static String label(OrderStatus status) {
        return status.name().charAt(0) + status.name().substring(1).toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** @param forSeller include the buyer's contact details (the seller needs them to follow up). */
    private OrderResponse toResponse(OrderRequest o, boolean forSeller) {
        Manufacturer m = o.getManufacturer();
        User b = o.getBuyer();
        boolean inrQuote = o.getQuotedTotal() != null && o.getCurrency() == Currency.INR;
        BigDecimal total = inrQuote ? o.getQuotedTotal()
                : o.getUnitPriceInr() == null ? null
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
                .source(o.getSource() != null ? o.getSource().name() : OrderSource.DIRECT.name())
                .rfqId(o.getRfqQuote() != null ? o.getRfqQuote().getRfq().getId() : null)
                .currency(o.getCurrency() != null ? o.getCurrency().name() : Currency.INR.name())
                .quotedTotal(o.getQuotedTotal())
                .contactName(o.getContactName())
                .contactPhone(o.getContactPhone())
                .deliveryAddress(o.getDeliveryAddress())
                .cancelReason(o.getCancelReason())
                .cancellable(!forSeller && BUYER_CANCELLABLE.contains(o.getStatus()))
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
