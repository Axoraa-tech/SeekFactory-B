package seekfactory.axoraa.enums;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

/**
 * Factory membership plans, as offered in the seller hub's "Global Enterprise Membership Plans".
 * Prices are annual, in USD, and are taken from here when a payment is submitted (the client never
 * sends an amount). An approved payment activates the plan and raises the product limit to at least
 * {@link #productLimit()}; "unlimited" plans use the highest limit the platform allows.
 */
public enum ManufacturerPlan {
    BASIC("Basic Listing", new BigDecimal("0"), 3),
    PREMIUM("Premium", new BigDecimal("1000"), 20),
    VIP("VIP Global Partner", new BigDecimal("10000"), 1000),
    STRATEGIC("Strategic Global VIP", new BigDecimal("20000"), 1000);

    private final String displayName;
    private final BigDecimal priceUsd;
    private final int productLimit;

    ManufacturerPlan(String displayName, BigDecimal priceUsd, int productLimit) {
        this.displayName = displayName;
        this.priceUsd = priceUsd;
        this.productLimit = productLimit;
    }

    public String displayName() { return displayName; }
    public BigDecimal priceUsd() { return priceUsd; }
    public int productLimit() { return productLimit; }

    /** The plan for a code such as "premium" or "VIP"; empty when unknown. */
    public static Optional<ManufacturerPlan> parse(String code) {
        if (code == null) return Optional.empty();
        try {
            return Optional.of(valueOf(code.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The stored plan of a factory; anything unknown or missing counts as BASIC. */
    public static ManufacturerPlan of(String stored) {
        return parse(stored).orElse(BASIC);
    }
}
