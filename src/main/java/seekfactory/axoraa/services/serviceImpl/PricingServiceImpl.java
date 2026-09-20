package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Response.pricing.SubscriptionPlanResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.SubscriptionPlan;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.SubscriptionPlanRepository;
import seekfactory.axoraa.services.services.PricingService;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PricingServiceImpl implements PricingService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final ManufacturerRepository manufacturerRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionPlanResponse> getAllPlans() {
        return subscriptionPlanRepository.findAll().stream()
                .map(plan -> SubscriptionPlanResponse.builder()
                        .id(plan.getId())
                        .name(plan.getName())
                        .priceUsd(plan.getPriceUsd())
                        .featuresJson(plan.getFeaturesJson())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void upgradeManufacturerSubscription(String manufacturerId, String subscriptionId) {
        Manufacturer manufacturer = manufacturerRepository.findById(manufacturerId)
                .orElseThrow(() -> new ResourceNotFoundException("Manufacturer", "id", manufacturerId));

        SubscriptionPlan newPlan = subscriptionPlanRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("SubscriptionPlan", "id", subscriptionId));

        manufacturer.setSubscriptionPlan(newPlan);
        
        // If they bought a premium tier, mark them as premium
        if (newPlan.getPriceUsd().doubleValue() > 0) {
            manufacturer.setPremium(true);
        }

        manufacturerRepository.save(manufacturer);
        log.info("Manufacturer {} upgraded to subscription plan {}", manufacturer.getName(), newPlan.getName());
    }
}
