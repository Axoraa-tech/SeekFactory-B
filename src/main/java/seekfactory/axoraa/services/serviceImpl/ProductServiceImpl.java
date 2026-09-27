package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Response.product.ProductDetailResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.entity.ProductSave;
import seekfactory.axoraa.entity.User;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.mapper.CatalogMapper;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.repository.ProductSaveRepository;
import seekfactory.axoraa.repository.UserRepository;
import seekfactory.axoraa.services.services.ProductService;
import seekfactory.axoraa.utils.CategoryTree;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Buyer-facing product catalog. Only active products of approved factories are visible.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private static final int RELATED_LIMIT = 6;

    private final ProductRepository productRepository;
    private final ProductSaveRepository productSaveRepository;
    private final UserRepository userRepository;
    private final CatalogMapper catalogMapper;
    private final CategoryTree categoryTree;

    @Override
    public List<ProductResponse> listTrending(int limit, String viewerId) {
        int effectiveLimit = limit > 0 ? Math.min(limit, 100) : 20;
        List<Product> trending = productRepository.findTrending(PageRequest.of(0, effectiveLimit));
        return catalogMapper.toProducts(trending, viewerId);
    }

    @Override
    public ProductDetailResponse getBySlug(String slug, String viewerId) {
        Product product = productRepository.findBySlug(slug)
                .filter(ProductServiceImpl::isVisible)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "slug", slug));

        Manufacturer manufacturer = product.getManufacturer();

        List<Product> related = productRepository
                .findByManufacturerIdAndIdNot(manufacturer.getId(), product.getId())
                .stream()
                .filter(ProductServiceImpl::isVisible)
                .limit(RELATED_LIMIT)
                .toList();

        return ProductDetailResponse.builder()
                .product(catalogMapper.toProducts(List.of(product), viewerId).getFirst())
                .manufacturer(catalogMapper.toManufacturer(manufacturer))
                .related(catalogMapper.toProducts(related, viewerId))
                .build();
    }

    @Override
    public List<ProductResponse> listByCategory(String categoryIdOrSlug, String viewerId) {
        Optional<Category> category = categoryTree.resolve(categoryIdOrSlug);
        if (category.isEmpty()) return List.of();
        List<Product> products = productRepository
                .findByCategoryIdInAndIsActiveTrueAndListedTrueAndManufacturerVerifiedTrueOrderByCreatedAtDesc(
                        categoryTree.withDescendants(category.get().getId()));
        return catalogMapper.toProducts(products, viewerId);
    }

    @Transactional
    @Override
    public Map<String, Object> toggleSave(String productId, String userId) {
        Product product = productRepository.findById(productId)
                .filter(ProductServiceImpl::isVisible)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        Optional<ProductSave> existing = productSaveRepository.findByProductIdAndUserId(productId, userId);
        if (existing.isPresent()) {
            productSaveRepository.delete(existing.get());
            return Map.of("saved", false);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        productSaveRepository.save(ProductSave.builder().product(product).user(user).build());
        return Map.of("saved", true);
    }

    @Override
    public List<ProductResponse> listSaved(String userId) {
        List<Product> products = productSaveRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ProductSave::getProduct)
                .filter(ProductServiceImpl::isVisible)
                .toList();
        return catalogMapper.toProducts(products, userId);
    }

    /** Buyers only see products that are not deleted, not paused by the seller, and from an approved factory. */
    private static boolean isVisible(Product product) {
        return product.isPubliclyVisible()
                && Boolean.TRUE.equals(product.getManufacturer().getVerified());
    }
}
