package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerDetailResponse;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;
import seekfactory.axoraa.dto.Response.reel.ReelResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.services.services.ManufacturerService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles manufacturer profile retrieval and listing.
 *
 * The getBySlug method returns a composite response containing:
 * - Manufacturer profile data
 * - All active products by this manufacturer
 * - All video reels by this manufacturer
 *
 * This matches the frontend ManufacturerDetail type in contracts.ts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ManufacturerServiceImpl implements ManufacturerService {

    private final ManufacturerRepository manufacturerRepository;
    private final ProductRepository productRepository;
    private final ReelRepository reelRepository;
    private final ModelMapper modelMapper;

    @Override
    public List<ManufacturerResponse> listVerified(int limit) {
        List<Manufacturer> verified = manufacturerRepository.findByVerifiedTrueOrderByFollowerCountDesc();

        // Apply limit
        if (limit > 0 && verified.size() > limit) {
            verified = verified.subList(0, limit);
        }

        return verified.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ManufacturerDetailResponse getBySlug(String slug) {
        Manufacturer manufacturer = manufacturerRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Manufacturer", "slug", slug));

        // An unapproved factory has no public profile
        if (!Boolean.TRUE.equals(manufacturer.getVerified())) {
            throw new ResourceNotFoundException("Manufacturer", "slug", slug);
        }

        // Fetch related products and reels
        List<Product> products = productRepository
                .findByManufacturerIdAndIsActiveTrue(manufacturer.getId());
        List<Reel> reels = reelRepository
                .findByManufacturerIdOrderByCreatedAtDesc(manufacturer.getId());

        return ManufacturerDetailResponse.builder()
                .manufacturer(mapToResponse(manufacturer))
                .products(products.stream()
                        .map(p -> modelMapper.map(p, ProductResponse.class))
                        .collect(Collectors.toList()))
                .reels(reels.stream()
                        .map(this::mapReelToResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    @Override
    public List<ManufacturerResponse> listAll() {
        // Buyer-facing: only factories an admin has approved
        return manufacturerRepository.findAll().stream()
                .filter(m -> Boolean.TRUE.equals(m.getVerified()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─── Private Helpers ──────────────────────────────────────

    private ManufacturerResponse mapToResponse(Manufacturer m) {
        ManufacturerResponse response = modelMapper.map(m, ManufacturerResponse.class);
        // Map ElementCollection and ManyToMany to simple lists
        response.setExportCountries(new ArrayList<>(m.getExportCountries()));
        response.setCategoryIds(m.getCategories().stream()
                .map(c -> c.getId())
                .collect(Collectors.toList()));
        return response;
    }

    private ReelResponse mapReelToResponse(Reel reel) {
        ReelResponse response = modelMapper.map(reel, ReelResponse.class);
        response.setViews(reel.getViewsCount());
        response.setLikes(reel.getLikesCount());
        response.setComments(reel.getCommentsCount());
        response.setShares(reel.getSharesCount());
        response.setSaves(reel.getSavesCount());
        response.setTab(reel.getFeedTab().name().toLowerCase().replace("_", "-"));
        response.setHashtags(new ArrayList<>(reel.getHashtags()));
        response.setProductIds(reel.getProducts().stream()
                .map(p -> p.getId())
                .collect(Collectors.toList()));
        return response;
    }
}