package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Response.search.SearchResponse;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.mapper.CatalogMapper;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.repository.Reels.ReelRepository;
import seekfactory.axoraa.services.services.SearchService;
import seekfactory.axoraa.utils.CategoryTree;
import seekfactory.axoraa.utils.InputUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchServiceImpl implements SearchService {

    private static final int MAX_LIMIT = 100;
    /** IN (...) needs at least one value even when the category filter is switched off. */
    private static final Set<String> NO_CATEGORY = Set.of("");

    private final ProductRepository productRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final ReelRepository reelRepository;
    private final CategoryTree categoryTree;
    private final CatalogMapper catalogMapper;

    @Override
    public SearchResponse search(String query, String category, int limit, String viewerId) {
        PageRequest page = PageRequest.of(0, Math.clamp(limit, 1, MAX_LIMIT));
        String pattern = InputUtils.likePattern(query);

        boolean allCategories = category == null || category.isBlank();
        Set<String> categoryIds = NO_CATEGORY;
        if (!allCategories) {
            Optional<Category> resolved = categoryTree.resolve(category);
            if (resolved.isEmpty()) {
                // An unknown category matches nothing rather than silently widening to everything
                return SearchResponse.builder().products(List.of()).manufacturers(List.of()).reels(List.of()).build();
            }
            categoryIds = categoryTree.withDescendants(resolved.get().getId());
        }

        return SearchResponse.builder()
                .products(catalogMapper.toProducts(
                        productRepository.search(pattern, allCategories, categoryIds, page), viewerId))
                .manufacturers(manufacturerRepository.search(pattern, allCategories, categoryIds, page).stream()
                        .map(catalogMapper::toManufacturer)
                        .toList())
                .reels(catalogMapper.toFeedItems(
                        reelRepository.search(pattern, allCategories, categoryIds, page), viewerId))
                .build();
    }
}
