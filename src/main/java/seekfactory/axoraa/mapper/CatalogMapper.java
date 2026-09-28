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
        return response;
    }

    public List<ProductResponse> toProducts(List<Product> products, String viewerId) {
        List<ProductResponse> responses = products.stream().map(this::toProduct).collect(Collectors.toList());
        if (viewerId != null && !responses.isEmpty()) {
            Set<String> saved = new HashSet<>(productSaveRepository.findSavedProductIds(viewerId,
                    responses.stream().map(ProductResponse::getId).toList()));
            responses.forEach(p -> p.setSavedByMe(saved.contains(p.getId())));
        }
        return responses;
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
        if (reels.isEmpty()) return List.of();

        Set<String> liked = Set.of();
        Set<String> saved = Set.of();
        Set<String> followed = Set.of();
        Set<String> savedProducts = Set.of();
        if (viewerId != null) {
            List<String> reelIds = reels.stream().map(Reel::getId).toList();
            liked = new HashSet<>(reelLikeRepository.findLikedReelIds(viewerId, reelIds));
            saved = new HashSet<>(reelSaveRepository.findSavedReelIds(viewerId, reelIds));
            followed = new HashSet<>(manufacturerFollowRepository.findFollowedManufacturerIds(viewerId,
                    reels.stream().map(r -> r.getManufacturer().getId()).collect(Collectors.toSet())));
            List<String> productIds = reels.stream().flatMap(r -> activeProducts(r).stream())
                    .map(Product::getId).distinct().toList();
            if (!productIds.isEmpty()) {
                savedProducts = new HashSet<>(productSaveRepository.findSavedProductIds(viewerId, productIds));
            }
        }

        // A factory usually appears many times in one page; map it once
        Map<String, ManufacturerResponse> manufacturers = new HashMap<>();
        List<FeedItemResponse> items = new ArrayList<>(reels.size());
        for (Reel reel : reels) {
            Manufacturer manufacturer = reel.getManufacturer();
            ReelResponse reelResponse = toReel(reel);
            List<Product> products = activeProducts(reel);
            List<ProductResponse> productResponses = products.stream().map(this::toProduct).collect(Collectors.toList());
            if (viewerId != null) {
                reelResponse.setLikedByMe(liked.contains(reel.getId()));
                reelResponse.setSavedByMe(saved.contains(reel.getId()));
                Set<String> savedIds = savedProducts;
                productResponses.forEach(p -> p.setSavedByMe(savedIds.contains(p.getId())));
            }
            items.add(FeedItemResponse.builder()
                    .reel(reelResponse)
                    .manufacturer(manufacturers.computeIfAbsent(manufacturer.getId(), id -> toManufacturer(manufacturer)))
                    .primaryProductSlug(products.stream().findFirst().map(Product::getSlug).orElse(null))
                    .products(productResponses)
                    .followingManufacturer(viewerId == null ? null : followed.contains(manufacturer.getId()))
                    .build());
        }
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
