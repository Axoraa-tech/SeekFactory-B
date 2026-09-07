package seekfactory.axoraa.enums;


/**
 * International Commercial Terms (Incoterms 2020).
 * Determines shipping responsibility split between buyer and supplier.
 */
public enum Incoterm {
    FOB,   // Free on Board — seller delivers to port
    CIF,   // Cost, Insurance & Freight — seller covers shipping + insurance
    EXW,   // Ex Works — buyer bears all transport
    DDP    // Delivered Duty Paid — seller covers everything to buyer's door
}
