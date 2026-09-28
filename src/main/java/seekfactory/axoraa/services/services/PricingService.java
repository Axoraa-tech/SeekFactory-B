package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.pricing.BuyerPlanResponse;
import seekfactory.axoraa.dto.Response.pricing.SubscriptionPlanResponse;

import java.util.List;

public interface PricingService {
    List<SubscriptionPlanResponse> getAllPlans();

    /** Buyer membership plans (free, pro, enterprise) with INR and CNY prices. */
    List<BuyerPlanResponse> getBuyerPlans();
    
    void upgradeManufacturerSubscription(String manufacturerId, String subscriptionId);
}
