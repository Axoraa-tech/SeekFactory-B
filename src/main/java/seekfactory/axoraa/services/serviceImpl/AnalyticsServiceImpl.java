package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.entity.ViewEvent;
import seekfactory.axoraa.enums.ViewEntityType;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.repository.ViewEventRepository;
import seekfactory.axoraa.services.services.AnalyticsService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * View counting rules:
 * - the viewer must be identifiable: signed-in user id, else a client-generated anonymous id;
 * - the factory's own account viewing its own content is not counted;
 * - the same viewer re-viewing the same item within {@link #DEDUPE_WINDOW} counts once.
 * A counted reel view also bumps reels.views_count, which the feed displays.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AnalyticsServiceImpl implements AnalyticsService {

    static final Duration DEDUPE_WINDOW = Duration.ofMinutes(30);
    private static final Pattern ANONYMOUS_ID = Pattern.compile("^[A-Za-z0-9-]{16,64}$");

    private final ViewEventRepository viewEventRepository;
    private final ReelRepository reelRepository;
    private final ProductRepository productRepository;

    @Override
    public boolean recordReelView(String reelId, String anonymousViewerId) {
        Reel reel = reelRepository.findById(reelId)
                .orElseThrow(() -> new ResourceNotFoundException("Reel", "id", reelId));
        boolean counted = record(ViewEntityType.REEL, reel.getId(), reel.getManufacturer(), anonymousViewerId);
        if (counted) {
            reelRepository.incrementViewsCount(reel.getId());
        }
        return counted;
    }

    @Override
    public boolean recordProductView(String productId, String anonymousViewerId) {
        Product product = productRepository.findById(productId)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
        return record(ViewEntityType.PRODUCT, product.getId(), product.getManufacturer(), anonymousViewerId);
    }

    private boolean record(ViewEntityType type, String entityId, Manufacturer manufacturer, String anonymousViewerId) {
        Optional<String> userId = SecurityUtils.findCurrentUserId();

        // A factory browsing its own seeks/products must not inflate its analytics
        if (userId.isPresent() && manufacturer.getUser() != null
                && userId.get().equals(manufacturer.getUser().getId())) {
            return false;
        }

        String viewerKey;
        if (userId.isPresent()) {
            viewerKey = "u:" + userId.get();
        } else if (anonymousViewerId != null && ANONYMOUS_ID.matcher(anonymousViewerId).matches()) {
            viewerKey = "a:" + anonymousViewerId;
        } else {
            return false; // unidentifiable guest: cannot dedupe, so do not count
        }

        Instant since = Instant.now().minus(DEDUPE_WINDOW);
        if (viewEventRepository.existsByEntityTypeAndEntityIdAndViewerKeyAndCreatedAtAfter(type, entityId, viewerKey, since)) {
            return false;
        }

        viewEventRepository.save(ViewEvent.builder()
                .entityType(type)
                .entityId(entityId)
                .manufacturerId(manufacturer.getId())
                .viewerKey(viewerKey)
                .build());
        return true;
    }
}
