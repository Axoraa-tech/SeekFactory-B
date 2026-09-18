package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.Response.product.ProductDetailResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.services.services.ProductService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles product listing, detail retrieval, and category-based filtering.
 *
 * The getBySlug method returns:
 * - Product details
 * - Manufacturer info (so the frontend can show the factory badge)
 * - Related products from the same manufacturer (excluding current product)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final ModelMapper modelMapper;

    @Override
    public List<ProductResponse> listTrending(int limit) {
        int effectiveLimit = limit > 0 ? limit : 20;
        List<Product> trending = productRepository.findTrending(PageRequest.of(0, effectiveLimit));

        return trending.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ProductDetailResponse getBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "slug", slug));

        Manufacturer manufacturer = product.getManufacturer();

        // Get related products from the same manufacturer, excluding current
        List<Product> related = productRepository
                .findByManufacturerIdAndIdNot(manufacturer.getId(), product.getId());

        // Limit related to 6
        if (related.size() > 6) {
            related = related.subList(0, 6);
        }

        ManufacturerResponse mfgResponse = modelMapper.map(manufacturer, ManufacturerResponse.class);
        mfgResponse.setExportCountries(new ArrayList<>(manufacturer.getExportCountries()));
        mfgResponse.setCategoryIds(manufacturer.getCategories().stream()
                .map(c -> c.getId())
                .collect(Collectors.toList()));

        return ProductDetailResponse.builder()
                .product(mapToResponse(product))
                .manufacturer(mfgResponse)
                .related(related.stream()
                        .map(this::mapToResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    @Override
    public List<ProductResponse> listByCategory(String categoryId) {
        return productRepository.findByCategoryIdAndIsActiveTrue(categoryId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─── Private Helpers ──────────────────────────────────────

    private ProductResponse mapToResponse(Product product) {
        ProductResponse response = modelMapper.map(product, ProductResponse.class);
        response.setManufacturerId(product.getManufacturer().getId());
        response.setCategoryId(product.getCategory().getId());
        return response;
    }
}