package seekfactory.axoraa.enums;


/**
 * Lifecycle stages of a Request for Quotation.
 * Follows the standard B2B procurement workflow.
 */


public enum RfqStatus {
    SUBMITTED,       // Buyer submitted, awaiting supplier matches
    REVIEWING,       // Platform reviewing and matching suppliers
    QUOTING,         // Sent to suppliers, quotes being collected
    QUOTED,          // At least one supplier quote received
    ACCEPTED,        // Buyer accepted a quote
    IN_PRODUCTION,   // Manufacturing started
    COMPLETED,       // Order fulfilled
    CANCELLED        // Cancelled by buyer or system
}
