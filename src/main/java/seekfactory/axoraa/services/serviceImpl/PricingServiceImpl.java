package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Response.pricing.BuyerPlanResponse;
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
    private final JdbcTemplate jdbcTemplate;

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
    @Transactional(readOnly = true)
    public List<BuyerPlanResponse> getBuyerPlans() {
        return jdbcTemplate.query("""
                SELECT code, name, price_inr, price_cny,
                       ARRAY(SELECT jsonb_array_elements_text(features_json)) AS features
                FROM buyer_plans
                ORDER BY sort_order
                """, (rs, i) -> BuyerPlanResponse.builder()
                .code(rs.getString("code").toLowerCase())
                .name(rs.getString("name"))
                .priceInr(rs.getBigDecimal("price_inr"))
                .priceCny(rs.getBigDecimal("price_cny"))
                .features(List.of((String[]) rs.getArray("features").getArray()))
                .build());
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
