package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Request.order.CartItemRequest;
import seekfactory.axoraa.dto.Request.order.CartQuantityRequest;
import seekfactory.axoraa.dto.Request.order.OrderCancelRequest;
import seekfactory.axoraa.dto.Request.order.OrderContactRequest;
import seekfactory.axoraa.dto.Request.order.OrderCreateRequest;
import seekfactory.axoraa.dto.Response.order.CartResponse;
import seekfactory.axoraa.dto.Request.order.OrderStatusUpdateRequest;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.order.OrderResponse;
import seekfactory.axoraa.services.services.OrderService;
import seekfactory.axoraa.services.services.ConversationService;
import seekfactory.axoraa.dto.Response.message.ConversationResponse;
import seekfactory.axoraa.utils.SecurityUtils;

import java.util.List;

/**
 * Order requests: buyers place them, the product's factory manages their status.
 * No payment is processed; SeekFactory connects the two parties.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Buyer order requests and seller status tracking")
public class OrderController {

    private final OrderService orderService;
    private final ConversationService conversationService;

    @PostMapping("/api/v1/orders")
    @Operation(summary = "Place an order request for a product (buyer contact is shared with the factory)")
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(@Valid @RequestBody OrderCreateRequest request) {
        OrderResponse order = orderService.placeOrder(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(order, "Order request sent to the factory"));
    }

    @GetMapping("/api/v1/orders/mine")
    @Operation(summary = "List the current user's order requests")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> myOrders() {
        return ResponseEntity.ok(ApiResponse.of(orderService.listBuyerOrders(SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/api/v1/orders/{orderId}/cancel")
    @Operation(summary = "Buyer withdraws an order request the factory has not confirmed yet")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable String orderId,
            @Valid @RequestBody(required = false) OrderCancelRequest request) {
        OrderResponse order = orderService.cancelByBuyer(SecurityUtils.getCurrentUserId(), orderId,
                request != null ? request.getReason() : null);
        return ResponseEntity.ok(ApiResponse.of(order, "Order request cancelled"));
    }

    // ─── Cart: checkout turns each line into an order request ──

    @GetMapping("/api/v1/cart")
    @Operation(summary = "Current user's cart")
    public ResponseEntity<ApiResponse<CartResponse>> getCart() {
        return ResponseEntity.ok(ApiResponse.of(orderService.getCart(SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/api/v1/cart/items")
    @Operation(summary = "Add a product to the cart (quantities of the same product are merged)")
    public ResponseEntity<ApiResponse<CartResponse>> addToCart(@Valid @RequestBody CartItemRequest request) {
        return ResponseEntity.ok(ApiResponse.of(orderService.addToCart(SecurityUtils.getCurrentUserId(), request),
                "Added to cart"));
    }

    @PutMapping("/api/v1/cart/items/{itemId}")
    @Operation(summary = "Change a cart line's quantity")
    public ResponseEntity<ApiResponse<CartResponse>> updateCartQuantity(
            @PathVariable String itemId,
            @Valid @RequestBody CartQuantityRequest request) {
        return ResponseEntity.ok(ApiResponse.of(orderService.updateCartQuantity(
                SecurityUtils.getCurrentUserId(), itemId, request.getQuantity())));
    }

    @DeleteMapping("/api/v1/cart/items/{itemId}")
    @Operation(summary = "Remove a cart line")
    public ResponseEntity<ApiResponse<CartResponse>> removeFromCart(@PathVariable String itemId) {
        return ResponseEntity.ok(ApiResponse.of(orderService.removeFromCart(SecurityUtils.getCurrentUserId(), itemId)));
    }

    @PostMapping("/api/v1/cart/checkout")
    @Operation(summary = "Send every cart line to its factory as an order request and empty the cart")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> checkout(@Valid @RequestBody OrderContactRequest request) {
        List<OrderResponse> orders = orderService.checkout(SecurityUtils.getCurrentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(orders, "Order requests sent to the factories"));
    }

    @GetMapping("/api/v1/factory/orders")
    @PreAuthorize("hasAuthority('ROLE_SUPPLIER')")
    @Operation(summary = "List order requests received by the current factory")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> factoryOrders() {
        return ResponseEntity.ok(ApiResponse.of(orderService.listFactoryOrders(SecurityUtils.getCurrentUserId())));
    }

    @PostMapping("/api/v1/factory/orders/{orderId}/conversation")
    @PreAuthorize("hasAuthority('ROLE_SUPPLIER')")
    @Operation(summary = "Open (or reuse) the chat with this order's buyer")
    public ResponseEntity<ApiResponse<ConversationResponse>> openConversation(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.of(
                conversationService.openForOrder(SecurityUtils.getCurrentUserId(), orderId)));
    }

    @PatchMapping("/api/v1/factory/orders/{orderId}/status")
    @PreAuthorize("hasAuthority('ROLE_SUPPLIER')")
    @Operation(summary = "Update an order request's status (and optional note to the buyer)")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable String orderId,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        OrderResponse order = orderService.updateStatus(SecurityUtils.getCurrentUserId(), orderId, request);
        return ResponseEntity.ok(ApiResponse.of(order, "Order status updated"));
    }
}
