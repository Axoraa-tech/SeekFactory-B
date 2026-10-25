package seekfactory.axoraa.mapper;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;
import seekfactory.axoraa.dto.Response.reel.FeedItemResponse;
import seekfactory.axoraa.dto.Response.reel.ReelResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.PriceTier;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.repository.ManufacturerFollowRepository;
import seekfactory.axoraa.repository.ProductSaveRepository;
import seekfactory.axoraa.repository.ProductLikeRepository;
import seekfactory.axoraa.repository.Reels.ReelLikeRepository;
import seekfactory.axoraa.repository.Reels.ReelSaveRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds buyer-facing catalog responses (factories, products, seeks) in one place,
 * including the viewer's own liked / saved / following state.
 *
 * Viewer state is looked up in batches (one query per kind per page), never per item.
 * Pass a null viewerId for guests: the viewer fields are then left null and omitted from JSON.
 * Callers must be inside a transaction because lazy associations are read.
 */
@Component
@RequiredArgsConstructor
public class CatalogMapper {

    private final ModelMapper modelMapper;
    private final ReelLikeRepository reelLikeRepository;
    private final ReelSaveRepository reelSaveRepository;
    private final ProductSaveRepository productSaveRepository;
    private final ProductLikeRepository productLikeRepository;
    private final ManufacturerFollowRepository manufacturerFollowRepository;

    public ManufacturerResponse toManufacturer(Manufacturer manufacturer) {
        ManufacturerResponse response = modelMapper.map(manufacturer, ManufacturerResponse.class);
        response.setExportCountries(new ArrayList<>(manufacturer.getExportCountries()));
        response.setCategoryIds(manufacturer.getCategories().stream()
                .map(c -> c.getId())
                .collect(Collectors.toList()));
        response.setCertifications(new ArrayList<>(manufacturer.getCertifications()));
        response.setCertificates(new ArrayList<>(manufacturer.getCertificates() != null ? manufacturer.getCertificates() : List.of()));
        return response;
    }

    public ProductResponse toProduct(Product product) {
        ProductResponse response = modelMapper.map(product, ProductResponse.class);
        response.setManufacturerId(product.getManufacturer().getId());
        response.setCategoryId(product.getCategory() != null ? product.getCategory().getId() : null);
        response.setImageUrls(product.gallery());
        response.setPriceTiers(sortedTiers(product.getPriceTiers()));
        response.setStockStatus(stockStatusOf(product));
        return response;
    }

    private static String stockStatusOf(Product product) {
        return product.getStockStatus() != null ? product.getStockStatus().name() : "IN_STOCK";
    }

    public List<ProductResponse> toProducts(List<Product> products, String viewerId) {
        return withViewerState(products.stream().map(this::toProduct).collect(Collectors.toList()), viewerId);
    }

    /**
     * Copies of viewer-neutral product responses (e.g. a cached list) with this viewer's saved and
     * like state and current like counts filled in. The given list and its items are not changed.
     */
    public List<ProductResponse> copyWithViewerState(List<ProductResponse> base, String viewerId) {
        return withViewerState(base.stream().map(p -> p.toBuilder().build()).collect(Collectors.toList()), viewerId);
    }

    private List<ProductResponse> withViewerState(List<ProductResponse> responses, String viewerId) {
        if (viewerId != null && !responses.isEmpty()) {
            Set<String> saved = new HashSet<>(productSaveRepository.findSavedProductIds(viewerId,
                    responses.stream().map(ProductResponse::getId).toList()));
            responses.forEach(p -> p.setSavedByMe(saved.contains(p.getId())));
        }
        enrichProductLikes(responses, viewerId);
        return responses;
    }

    /** One grouped count query plus one viewer query for the whole page, never per card. */
    private void enrichProductLikes(List<ProductResponse> responses, String viewerId) {
        if (responses.isEmpty()) return;
        List<String> ids = responses.stream().map(ProductResponse::getId).distinct().toList();
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : productLikeRepository.countForProducts(ids)) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }
        Set<String> liked = viewerId == null ? Set.of() : new HashSet<>(productLikeRepository.findLikedProductIds(viewerId, ids));
        responses.forEach(response -> {
            response.setLikesCount(counts.getOrDefault(response.getId(), 0L));
            response.setLikedByMe(viewerId == null ? null : liked.contains(response.getId()));
        });
    }

    public ReelResponse toReel(Reel reel) {
        return ReelResponse.builder()
                .id(reel.getId())
                .manufacturerId(reel.getManufacturer().getId())
                .title(reel.getTitle())
                .description(reel.getDescription())
                .hashtags(new ArrayList<>(reel.getHashtags()))
                .posterUrl(reel.getPosterUrl())
                .videoUrl(reel.getVideoUrl())
                .durationSec(reel.getDurationSec())
                .startSec(reel.getStartSec())
                .views(reel.getViewsCount())
                .likes(reel.getLikesCount())
                .comments(reel.getCommentsCount())
                .shares(reel.getSharesCount())
                .saves(reel.getSavesCount())
                .tab(reel.getFeedTab().name().toLowerCase().replace("_", "-"))
                .productIds(activeProducts(reel).stream().map(Product::getId).collect(Collectors.toList()))
                .build();
    }

    public List<FeedItemResponse> toFeedItems(List<Reel> reels, String viewerId) {
        return feedWithViewerState(toNeutralFeedItems(reels), viewerId);
    }

    /** Feed items without anything viewer-specific: the same for everyone, so they can be shared. */
    public List<FeedItemResponse> toNeutralFeedItems(List<Reel> reels) {
        if (reels.isEmpty()) return List.of();
        // A factory usually appears many times in one page; map it once
        Map<String, ManufacturerResponse> manufacturers = new HashMap<>();
        List<FeedItemResponse> items = new ArrayList<>(reels.size());
        for (Reel reel : reels) {
            Manufacturer manufacturer = reel.getManufacturer();
            List<Product> products = activeProducts(reel);
            items.add(FeedItemResponse.builder()
                    .reel(toReel(reel))
                    .manufacturer(manufacturers.computeIfAbsent(manufacturer.getId(), id -> toManufacturer(manufacturer)))
                    .primaryProductSlug(products.stream().findFirst().map(Product::getSlug).orElse(null))
                    .products(products.stream().map(this::toProduct).collect(Collectors.toList()))
                    .build());
        }
        return items;
    }

    /**
     * Copies of viewer-neutral feed items (e.g. a cached page) with this viewer's reactions, saves
     * and follows and current product like counts. The given items are not changed.
     */
    public List<FeedItemResponse> copyFeedWithViewerState(List<FeedItemResponse> base, String viewerId) {
        List<FeedItemResponse> copies = base.stream().map(item -> item.toBuilder()
                        .reel(item.getReel().toBuilder().build())
                        .products(item.getProducts().stream().map(p -> p.toBuilder().build()).collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
        return feedWithViewerState(copies, viewerId);
    }

    private List<FeedItemResponse> feedWithViewerState(List<FeedItemResponse> items, String viewerId) {
        if (items.isEmpty()) return items;
        if (viewerId != null) {
            List<String> reelIds = items.stream().map(item -> item.getReel().getId()).toList();
            Map<String, String> reactions = new HashMap<>();
            for (Object[] row : reelLikeRepository.findReactions(viewerId, reelIds)) {
                reactions.put((String) row[0], (String) row[1]);
            }
            Set<String> saved = new HashSet<>(reelSaveRepository.findSavedReelIds(viewerId, reelIds));
            Set<String> followed = new HashSet<>(manufacturerFollowRepository.findFollowedManufacturerIds(viewerId,
                    items.stream().map(item -> item.getManufacturer().getId()).collect(Collectors.toSet())));
            List<String> productIds = items.stream().flatMap(item -> item.getProducts().stream())
                    .map(ProductResponse::getId).distinct().toList();
            Set<String> savedProducts = productIds.isEmpty() ? Set.of()
                    : new HashSet<>(productSaveRepository.findSavedProductIds(viewerId, productIds));
            for (FeedItemResponse item : items) {
                ReelResponse reel = item.getReel();
                reel.setLikedByMe(reactions.containsKey(reel.getId()));
                reel.setMyReaction(reactions.get(reel.getId()));
                reel.setSavedByMe(saved.contains(reel.getId()));
                item.getProducts().forEach(p -> p.setSavedByMe(savedProducts.contains(p.getId())));
                item.setFollowingManufacturer(followed.contains(item.getManufacturer().getId()));
            }
        }
        enrichProductLikes(items.stream().flatMap(item -> item.getProducts().stream()).toList(), viewerId);
        return items;
    }

    /** Deleted (soft-deleted) and seller-paused products must never reach buyers. */
    private static List<Product> activeProducts(Reel reel) {
        return reel.getProducts().stream()
                .filter(Product::isPubliclyVisible)
                .sorted(Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    private static List<PriceTier> sortedTiers(List<PriceTier> tiers) {
        if (tiers == null) return List.of();
        return tiers.stream()
                .filter(t -> t.getMinQty() != null && t.getPriceInr() != null)
                .sorted(Comparator.comparing(PriceTier::getMinQty))
                .collect(Collectors.toList());
    }
}
