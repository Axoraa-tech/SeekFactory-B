package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Request.order.OrderCreateRequest;
import seekfactory.axoraa.dto.Request.order.OrderStatusUpdateRequest;
import seekfactory.axoraa.dto.Response.order.OrderResponse;

import java.util.List;

/**
 * Buyer order requests. No payment is taken: the order notifies the seller, who
 * contacts the buyer and tracks the deal status.
 */
public interface OrderService {

    OrderResponse placeOrder(String buyerUserId, OrderCreateRequest request);

    List<OrderResponse> listBuyerOrders(String buyerUserId);

    List<OrderResponse> listFactoryOrders(String supplierUserId);

    /** Orders between one buyer and one factory, newest first. */
    List<OrderResponse> listOrdersBetween(String buyerUserId, String manufacturerId, boolean forSeller);

    OrderResponse updateStatus(String supplierUserId, String orderId, OrderStatusUpdateRequest request);
}
