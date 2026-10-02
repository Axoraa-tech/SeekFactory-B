package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.pricing.BuyerPlanResponse;
import seekfactory.axoraa.dto.Response.pricing.SubscriptionPlanResponse;

import java.math.BigDecimal;
import java.util.List;

public interface PricingService {
    List<SubscriptionPlanResponse> getAllPlans();

    /** Buyer membership plans (free, pro, enterprise) with INR and CNY prices. */
    List<BuyerPlanResponse> getBuyerPlans();

    /** Admin: sets the monthly INR and CNY price of a buyer plan (code is case-insensitive). */
    void updateBuyerPlanPrices(String code, BigDecimal priceInr, BigDecimal priceCny);

    void upgradeManufacturerSubscription(String manufacturerId, String subscriptionId);
}
