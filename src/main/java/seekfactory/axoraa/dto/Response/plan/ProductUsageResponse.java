package seekfactory.axoraa.dto.Response.plan;

/**
 * The manufacturer's product allowance: live products, the limit, and the latest upgrade
 * request (null if none was ever made).
 */
public record ProductUsageResponse(
        int productCount, int productLimit, boolean canAddProduct, ProductLimitRequestResponse latestRequest,
        /** Membership plan code, lower case: basic, premium, vip or strategic. */
        String membershipPlan) {}
