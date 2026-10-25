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
        return search(query, category, limit, viewerId, true);
    }

    @Override
    public SearchResponse search(String query, String category, int limit, String viewerId, boolean includeReels) {
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

        // Matching products and factories are the same for everyone (Explore reloads them on every
        // visit and sort change): shared briefly. Viewer state is added per request; seeks carry
        // per-viewer likes, saves and follows, so they are searched fresh.
        Set<String> ids = categoryIds;
        boolean all = allCategories;
        Neutral neutral = neutralCache.get(pattern + "|" + (all ? "*" : String.join(",", new java.util.TreeSet<>(ids))) + "|" + page.getPageSize(),
                () -> inReadOnlyTx(() -> new Neutral(
                        productRepository.search(pattern, all, ids, page).stream().map(catalogMapper::toProduct).toList(),
                        manufacturerRepository.search(pattern, all, ids, page).stream().map(catalogMapper::toManufacturer).toList())));

        return SearchResponse.builder()
                .products(catalogMapper.copyWithViewerState(neutral.products(), viewerId))
                .manufacturers(neutral.manufacturers())
                .reels(includeReels
                        ? catalogMapper.toFeedItems(reelRepository.search(pattern, allCategories, categoryIds, page), viewerId)
                        : List.of())
                .build();
    }

    private record Neutral(List<seekfactory.axoraa.dto.Response.product.ProductResponse> products,
                           List<seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse> manufacturers) {}

    /** Recent searches (term, categories, size): fresh for a minute, rebuilt in the background. */
    private final seekfactory.axoraa.utils.RefreshingCache<String, Neutral> neutralCache =
            new seekfactory.axoraa.utils.RefreshingCache<>(60_000, 10 * 60_000, 300);

    /** A product, seek, factory or category changed: serve the old copy once and rebuild behind it. */
    @org.springframework.context.event.EventListener
    void onCatalogChanged(seekfactory.axoraa.config.CatalogChangeListener.CatalogChangedEvent event) {
        neutralCache.markStale();
    }

    /** Background cache rebuilds run outside any request: they open their own read-only transaction. */
    private org.springframework.transaction.support.TransactionTemplate readOnlyTx;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setTransactionManager(org.springframework.transaction.PlatformTransactionManager transactionManager) {
        readOnlyTx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        readOnlyTx.setReadOnly(true);
    }

    private <T> T inReadOnlyTx(java.util.function.Supplier<T> work) {
        return readOnlyTx == null ? work.get() : readOnlyTx.execute(status -> work.get());
    }
}
