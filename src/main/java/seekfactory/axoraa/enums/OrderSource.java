package seekfactory.axoraa.enums;

/**
 * How an order was created: checked out from the cart, "Buy Now" on a product, or an accepted RFQ quote.
 */
public enum OrderSource {
    CART,
    DIRECT,
    RFQ_QUOTE
}
