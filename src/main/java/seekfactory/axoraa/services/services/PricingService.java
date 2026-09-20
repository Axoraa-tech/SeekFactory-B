package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.pricing.SubscriptionPlanResponse;

import java.util.List;

public interface PricingService {
    List<SubscriptionPlanResponse> getAllPlans();
    
    void upgradeManufacturerSubscription(String manufacturerId, String subscriptionId);
}
