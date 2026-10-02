package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Request.order.CartItemRequest;
import seekfactory.axoraa.dto.Request.order.CartCheckoutRequest;
import seekfactory.axoraa.dto.Request.order.OrderContactRequest;
import seekfactory.axoraa.dto.Request.order.OrderCreateRequest;
import seekfactory.axoraa.dto.Request.order.OrderStatusUpdateRequest;
import seekfactory.axoraa.dto.Response.order.CartResponse;
import seekfactory.axoraa.dto.Response.order.OrderResponse;
import seekfactory.axoraa.entity.Rfqs.RfqQuote;

import java.util.List;

/**
 * Buyer order requests. No payment is taken: the order notifies the seller, who
 * contacts the buyer and tracks the deal status.
 *
 * Requests come from a product page ({@link #placeOrder}), the cart ({@link #checkout}, one
 * request per product) or an accepted RFQ quote ({@link #createFromQuote}).
 */
public interface OrderService {

    OrderResponse placeOrder(String buyerUserId, OrderCreateRequest request);

    List<OrderResponse> listBuyerOrders(String buyerUserId);

    List<OrderResponse> listFactoryOrders(String supplierUserId);

    /** Orders between one buyer and one factory, newest first. */
    List<OrderResponse> listOrdersBetween(String buyerUserId, String manufacturerId, boolean forSeller);

    OrderResponse updateStatus(String supplierUserId, String orderId, OrderStatusUpdateRequest request);

    /** Buyer withdraws a request the factory has not confirmed yet. */
    OrderResponse cancelByBuyer(String buyerUserId, String orderId, String reason);

    /** Called by the RFQ flow when the buyer accepts a quote. */
    OrderResponse createFromQuote(RfqQuote quote, OrderContactRequest contact);

    // ─── Cart ─────────────────────────────────────────────────

    CartResponse getCart(String userId);

    CartResponse addToCart(String userId, CartItemRequest request);

    CartResponse updateCartQuantity(String userId, String cartItemId, int quantity);

    CartResponse removeFromCart(String userId, String cartItemId);

    /** Turns every cart line into an order request and empties the cart. */
    List<OrderResponse> checkout(String userId, CartCheckoutRequest request);
}
