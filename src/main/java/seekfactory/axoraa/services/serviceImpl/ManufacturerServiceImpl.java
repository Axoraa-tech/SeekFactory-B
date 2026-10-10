package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerDetailResponse;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.ManufacturerFollow;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.enums.NotificationType;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.mapper.CatalogMapper;
import seekfactory.axoraa.repository.ManufacturerFollowRepository;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.FactoryService;
import seekfactory.axoraa.services.services.ManufacturerService;
import seekfactory.axoraa.services.services.NotificationService;
import seekfactory.axoraa.services.services.ResponseMetrics;

import org.springframework.data.domain.PageRequest;
import seekfactory.axoraa.exceptions.BadRequestException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Buyer-facing factory profiles and follows. Only admin-approved factories are visible.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManufacturerServiceImpl implements ManufacturerService {

    private final ManufacturerRepository manufacturerRepository;
    private final ManufacturerFollowRepository manufacturerFollowRepository;
    private final ProductRepository productRepository;
    private final ReelRepository reelRepository;
    private final UserRepository userRepository;
    private final CatalogMapper catalogMapper;
    private final FactoryService factoryService;
    private final NotificationService notificationService;

    @Override
    public List<ManufacturerResponse> listVerified(int limit) {
        VerifiedCache cached = verifiedCache.get(limit);
        if (cached != null && System.currentTimeMillis() < cached.expiresAt()) return cached.manufacturers();
        // Limit in SQL rather than loading every approved factory first
        List<Manufacturer> verified = limit > 0
                ? manufacturerRepository.findByVerifiedTrueOrderByFollowerCountDesc(PageRequest.of(0, limit))
                : manufacturerRepository.findByVerifiedTrueOrderByFollowerCountDesc();
        List<ManufacturerResponse> result = verified.stream()
                .map(catalogMapper::toManufacturer)
                .toList();
        if (verifiedCache.size() >= 16) verifiedCache.clear();  // keyed by limit: stays tiny
        verifiedCache.put(limit, new VerifiedCache(result, System.currentTimeMillis() + VERIFIED_TTL_MS));
        return result;
    }

    // Public "verified factories" strip: identical for every viewer and changes rarely
    private static final long VERIFIED_TTL_MS = 120_000;
    private record VerifiedCache(List<ManufacturerResponse> manufacturers, long expiresAt) {}
    private final Map<Integer, VerifiedCache> verifiedCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public ManufacturerDetailResponse getBySlug(String slug, String viewerId) {
        Manufacturer manufacturer = manufacturerRepository.findBySlug(slug)
                .filter(m -> Boolean.TRUE.equals(m.getVerified()))  // an unapproved factory has no public profile
                .orElseThrow(() -> new ResourceNotFoundException("Manufacturer", "slug", slug));

        // An unapproved factory has no public profile
        if (!Boolean.TRUE.equals(manufacturer.getVerified())) {
            throw new ResourceNotFoundException("Manufacturer", "slug", slug);
        }

        // Buyer-facing: only listings the seller has not paused
        List<Product> products = productRepository
                .findByManufacturerIdAndIsActiveTrueAndListedTrue(manufacturer.getId());
        List<Reel> reels = reelRepository
                .findByManufacturerIdAndListedTrueOrderByCreatedAtDesc(manufacturer.getId());
        ResponseMetrics metrics = factoryService.getResponseMetrics(manufacturer);

        return ManufacturerDetailResponse.builder()
                .manufacturer(catalogMapper.toManufacturer(manufacturer))
                .products(catalogMapper.toProducts(products, viewerId))
                .reels(reels.stream().map(catalogMapper::toReel).collect(Collectors.toList()))
                .certifications(manufacturer.getCertifications().stream().sorted().toList())
                .responseRatePercent(metrics.responseRatePercent())
                .avgResponseTimeHours(metrics.avgResponseTimeHours())
                .followedByMe(viewerId == null ? null
                        : manufacturerFollowRepository.existsByManufacturerIdAndUserId(manufacturer.getId(), viewerId))
                .build();
    }

    @Override
    public List<ManufacturerResponse> listAll() {
        // Buyer-facing: only approved factories, filtered in SQL rather than after
        // loading every manufacturer into memory.
        return manufacturerRepository.findByVerifiedTrue().stream()
                .map(catalogMapper::toManufacturer)
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public Map<String, Object> toggleFollow(String manufacturerId, String userId) {
        Optional<ManufacturerFollow> existing =
                manufacturerFollowRepository.findByManufacturerIdAndUserId(manufacturerId, userId);
        // Unfollowing always works; following needs an approved factory that is not the user's own
        Manufacturer manufacturer = manufacturerRepository.findById(manufacturerId)
                .filter(m -> existing.isPresent() || Boolean.TRUE.equals(m.getVerified()))
                .orElseThrow(() -> new ResourceNotFoundException("Manufacturer", "id", manufacturerId));
        if (existing.isEmpty() && manufacturer.getUser() != null && userId.equals(manufacturer.getUser().getId())) {
            throw new BadRequestException("You cannot follow your own factory");
        }
        boolean following;
        if (existing.isPresent()) {
            manufacturerFollowRepository.delete(existing.get());
            manufacturerFollowRepository.flush();
            following = false;
        } else {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
            manufacturerFollowRepository.saveAndFlush(ManufacturerFollow.builder()
                    .manufacturer(manufacturer)
                    .user(user)
                    .build());
            following = true;
            // One alert per follower until the factory reads it, so follow/unfollow toggling cannot spam
            String who = user.getCompanyName() != null && !user.getCompanyName().isBlank()
                    ? user.getName() + " (" + user.getCompanyName() + ")" : user.getName();
            notificationService.notifyOnce(manufacturer.getUser(), NotificationType.FOLLOW,
                    "New follower", who + " started following " + manufacturer.getName() + ".", userId);
        }

        // Recount rather than increment so concurrent toggles cannot drift the number
        int followerCount = (int) manufacturerFollowRepository.countByManufacturerId(manufacturerId);
        manufacturer.setFollowerCount(followerCount);
        manufacturerRepository.save(manufacturer);
        return Map.of("following", following, "followerCount", followerCount);
    }

    @Override
    public List<ManufacturerResponse> listFollowing(String userId) {
        return manufacturerFollowRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ManufacturerFollow::getManufacturer)
                .filter(m -> Boolean.TRUE.equals(m.getVerified()))
                .map(catalogMapper::toManufacturer)
                .collect(Collectors.toList());
    }
}
