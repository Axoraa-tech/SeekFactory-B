package seekfactory.axoraa.enums;

/**
 * Lifecycle of a buyer order request. SeekFactory takes no payment: the buyer
 * signals intent, and the seller moves the status as the deal progresses offline.
 */
public enum OrderStatus {
    PENDING,       // Buyer placed the order request; seller has not acted yet
    CONTACTED,     // Seller reached out to the buyer
    NEGOTIATING,   // Price / specs / delivery being discussed
    CONFIRMED,     // Both sides agreed; production or dispatch arranged
    COMPLETED,     // Delivered / fulfilled
    CANCELLED      // Dropped by either side
}
