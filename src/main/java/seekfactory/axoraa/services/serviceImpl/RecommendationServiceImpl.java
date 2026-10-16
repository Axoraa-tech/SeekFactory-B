package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Response.product.ProductResponse;
import seekfactory.axoraa.dto.Response.product.RecommendedProductsResponse;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.enums.ProductStockStatus;
import seekfactory.axoraa.mapper.CatalogMapper;
import seekfactory.axoraa.repository.CategoryRepository;
import seekfactory.axoraa.repository.ManufacturerRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.services.services.RecommendationService;
import seekfactory.axoraa.utils.InputUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Feed side-panel products. Interest is scored per category from the viewer's activity, newer
 * activity counting more (30-day decay):
 *   order 5 · cart 4 · save 3 · RFQ 3 · search 2 · product view 1
 * Products matching recent searches come first, then the best categories' products (and their
 * sibling categories', for "similar"), round-robin so no one category floods the list, then
 * trending. Out-of-stock products go last; one factory never gets more than two in a row.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationServiceImpl implements RecommendationService {

    static final int MAX_LIMIT = 60;
    private static final int MAX_TERMS = 5;
    private static final int MAX_TERM_LENGTH = 60;
    private static final int SEARCH_MATCHES_PER_TERM = 8;
    private static final int TOP_CATEGORIES = 6;
    private static final double SEARCH_WEIGHT = 2.0;
    /** Sibling categories count for this share of the category they sit next to. */
    private static final double SIBLING_SHARE = 0.4;
    private static final int MAX_RUN_PER_FACTORY = 2;
    private static final long CACHE_TTL_MS = 120_000;
    private static final int CACHE_MAX_ENTRIES = 500;
    private static final Pattern ANONYMOUS_ID = Pattern.compile("^[A-Za-z0-9-]{16,64}$");

    /** Category interest from the viewer's own activity, decayed over 30 days. */
    private static final String CATEGORY_SCORES_SQL = """
            WITH signals AS (
                SELECT p.category_id, 5.0 AS weight, o.created_at
                  FROM order_requests o JOIN products p ON p.id = o.product_id
                 WHERE o.buyer_id = :userId
                UNION ALL
                SELECT p.category_id, 4.0, c.created_at
                  FROM cart_items c JOIN products p ON p.id = c.product_id
                 WHERE c.user_id = :userId
                UNION ALL
                SELECT p.category_id, 3.0, s.created_at
                  FROM product_saves s JOIN products p ON p.id = s.product_id
                 WHERE s.user_id = :userId
                UNION ALL
                SELECT r.category_id, 3.0, r.created_at
                  FROM rfqs r
                 WHERE r.user_id = :userId AND r.category_id IS NOT NULL
                UNION ALL
                SELECT p.category_id, 1.0, v.created_at
                  FROM view_events v JOIN products p ON p.id = v.entity_id
                 WHERE v.entity_type = 'PRODUCT' AND v.viewer_key IN (:viewerKeys)
                   AND v.created_at > now() - interval '90 days'
            )
            SELECT category_id,
                   SUM(weight * exp(-extract(epoch FROM (now() - created_at)) / 2592000.0)) AS score
              FROM signals
             GROUP BY category_id
             ORDER BY score DESC
             LIMIT 20
            """;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ManufacturerRepository manufacturerRepository;
    private final CatalogMapper catalogMapper;
    private final NamedParameterJdbcTemplate jdbc;

    private record CacheEntry(RecommendedProductsResponse response, long expiresAt) {}
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public RecommendedProductsResponse recommend(String userId, String anonymousViewerId, List<String> searchTerms, int limit) {
        int effectiveLimit = limit > 0 ? Math.min(limit, MAX_LIMIT) : MAX_LIMIT;
        String anonId = anonymousViewerId != null && ANONYMOUS_ID.matcher(anonymousViewerId).matches() ? anonymousViewerId : null;
        List<String> terms = cleanTerms(searchTerms);

        String cacheKey = (userId != null ? "u:" + userId : "") + "|" + (anonId != null ? anonId : "") + "|"
                + String.join("\u0001", terms) + "|" + effectiveLimit;
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && System.currentTimeMillis() < cached.expiresAt()) return cached.response();

        RecommendedProductsResponse response = build(userId, anonId, terms, effectiveLimit);
        if (cache.size() >= CACHE_MAX_ENTRIES) cache.clear();  // per-viewer and short-lived: just start over
        cache.put(cacheKey, new CacheEntry(response, System.currentTimeMillis() + CACHE_TTL_MS));
        return response;
    }

    private RecommendedProductsResponse build(String userId, String anonId, List<String> terms, int limit) {
        // 1. Products matching recent searches, newest search first
        List<Product> searchMatches = new ArrayList<>();
        Map<String, Double> scores = new HashMap<>();
        for (int i = 0; i < terms.size(); i++) {
            List<Product> matches = productRepository.search(InputUtils.likePattern(terms.get(i)), true,
                    Set.of("-"), PageRequest.of(0, SEARCH_MATCHES_PER_TERM));
            searchMatches.addAll(matches);
            // Older searches count a little less; the term's weight is shared across its categories
            double weight = SEARCH_WEIGHT * (1.0 - 0.1 * i);
            for (Product p : matches) {
                if (p.getCategory() != null) scores.merge(p.getCategory().getId(), weight / matches.size(), Double::sum);
            }
        }

        // 2. Category interest from orders, cart, saves, RFQs and views
        List<String> viewerKeys = new ArrayList<>();
        if (userId != null) viewerKeys.add("u:" + userId);
        if (anonId != null) viewerKeys.add("a:" + anonId);
        if (!viewerKeys.isEmpty()) {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("userId", userId)
                    .addValue("viewerKeys", viewerKeys);
            jdbc.query(CATEGORY_SCORES_SQL, params,
                    rs -> { scores.merge(rs.getString("category_id"), rs.getDouble("score"), Double::sum); });
        }

        // 3. Top categories plus their siblings, then their products
        Map<String, Double> categoryScores = withSiblings(scores);
        List<String> rankedCategories = categoryScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
        List<Product> categoryProducts = rankedCategories.isEmpty() ? List.of()
                : productRepository.findVisibleInCategories(rankedCategories, PageRequest.of(0, limit * 2));

        String ownFactoryId = userId == null ? null
                : manufacturerRepository.findByUserId(userId).map(Manufacturer::getId).orElse(null);

        // 4. Trending tops up whatever is left; skipped when interests already fill the panel
        Set<String> personalIds = new HashSet<>();
        searchMatches.forEach(p -> personalIds.add(p.getId()));
        categoryProducts.forEach(p -> personalIds.add(p.getId()));
        long usable = searchMatches.stream().filter(p -> !p.getManufacturer().getId().equals(ownFactoryId)).count()
                + categoryProducts.stream().filter(p -> !p.getManufacturer().getId().equals(ownFactoryId)).count();
        List<Product> trending = personalIds.size() >= limit && usable >= limit ? List.of()
                : productRepository.findTrending(PageRequest.of(0, limit + 20));

        List<Product> ranked = assemble(searchMatches, rankedCategories, categoryProducts, trending, ownFactoryId, limit);
        boolean personalized = ranked.stream().anyMatch(p -> personalIds.contains(p.getId()));

        List<ProductResponse> products = catalogMapper.toProducts(ranked, userId);
        Map<String, RecommendedProductsResponse.Factory> factories = new LinkedHashMap<>();
        for (Product p : ranked) {
            Manufacturer m = p.getManufacturer();
            factories.computeIfAbsent(m.getId(), id -> RecommendedProductsResponse.Factory.builder()
                    .id(m.getId())
                    .slug(m.getSlug())
                    .name(m.getName())
                    .logo(m.getLogoUrl())
                    .country(m.getCountry())
                    .verified(m.getVerified())
                    .build());
        }
        return RecommendedProductsResponse.builder()
                .personalized(personalized)
                .products(products)
                .manufacturers(new ArrayList<>(factories.values()))
                .build();
    }

    /** The top categories plus their sibling categories (same parent) at a reduced share. */
    private Map<String, Double> withSiblings(Map<String, Double> scores) {
        if (scores.isEmpty()) return Map.of();
        List<Category> all = categoryRepository.findAll();
        Map<String, String> parentOf = new HashMap<>();
        Map<String, List<String>> childrenOf = new HashMap<>();
        for (Category c : all) {
            String parent = c.getParent() != null ? c.getParent().getId() : null;
            parentOf.put(c.getId(), parent);
            if (parent != null) childrenOf.computeIfAbsent(parent, k -> new ArrayList<>()).add(c.getId());
        }

        Map<String, Double> result = new HashMap<>();
        scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(TOP_CATEGORIES)
                .forEach(e -> {
                    String id = e.getKey();
                    result.merge(id, e.getValue(), Math::max);
                    // A root category: its own subcategories are the "similar" ones
                    List<String> related = parentOf.get(id) == null
                            ? childrenOf.getOrDefault(id, List.of())
                            : childrenOf.getOrDefault(parentOf.get(id), List.of());
                    for (String sibling : related) {
                        if (!sibling.equals(id)) result.merge(sibling, e.getValue() * SIBLING_SHARE, Math::max);
                    }
                });
        return result;
    }

    /**
     * Orders the panel: search matches, then category products round-robin in category order,
     * then trending; without duplicates or the viewer's own products. In-stock first, and no factory
     * more than {@value #MAX_RUN_PER_FACTORY} times in a row where another factory can step in.
     */
    static List<Product> assemble(List<Product> searchMatches, List<String> rankedCategories, List<Product> categoryProducts,
                                  List<Product> trending, String ownFactoryId, int limit) {
        LinkedHashMap<String, Product> picked = new LinkedHashMap<>();
        java.util.function.Consumer<Product> add = p -> {
            if (picked.size() >= limit * 2) return;  // enough material for the reordering below
            if (ownFactoryId != null && ownFactoryId.equals(p.getManufacturer().getId())) return;
            picked.putIfAbsent(p.getId(), p);
        };

        searchMatches.forEach(add);

        Map<String, Deque<Product>> byCategory = new LinkedHashMap<>();
        rankedCategories.forEach(id -> byCategory.put(id, new ArrayDeque<>()));
        for (Product p : categoryProducts) {
            String categoryId = p.getCategory() != null ? p.getCategory().getId() : null;
            Deque<Product> queue = byCategory.get(categoryId);
            if (queue != null) queue.add(p);
        }
        boolean more = true;
        while (more) {
            more = false;
            for (Deque<Product> queue : byCategory.values()) {
                Product next = queue.poll();
                if (next != null) {
                    add.accept(next);
                    more = true;
                }
            }
        }

        trending.forEach(add);

        List<Product> ordered = new ArrayList<>(picked.values());
        // Stable: keeps the relevance order within each group
        ordered.sort(Comparator.comparing(p -> p.getStockStatus() == ProductStockStatus.OUT_OF_STOCK));
        return spreadFactories(ordered).stream().limit(limit).collect(Collectors.toList());
    }

    /** Moves a product down when it would be a factory's third in a row and another factory's is available. */
    static List<Product> spreadFactories(List<Product> products) {
        List<Product> pending = new ArrayList<>(products);
        List<Product> result = new ArrayList<>(products.size());
        while (!pending.isEmpty()) {
            int pick = 0;
            if (result.size() >= MAX_RUN_PER_FACTORY) {
                String last = factoryOf(result.get(result.size() - 1));
                boolean run = true;
                for (int i = 2; i <= MAX_RUN_PER_FACTORY; i++) {
                    run &= last.equals(factoryOf(result.get(result.size() - i)));
                }
                if (run) {
                    for (int i = 0; i < pending.size(); i++) {
                        if (!last.equals(factoryOf(pending.get(i)))) {
                            pick = i;
                            break;
                        }
                    }
                }
            }
            result.add(pending.remove(pick));
        }
        return result;
    }

    private static String factoryOf(Product p) {
        return p.getManufacturer().getId();
    }

    /** Trimmed, de-duplicated (case-insensitive) search terms, newest first, at most {@value #MAX_TERMS}. */
    static List<String> cleanTerms(List<String> terms) {
        if (terms == null) return List.of();
        LinkedHashMap<String, String> seen = new LinkedHashMap<>();
        for (String raw : terms) {
            if (raw == null) continue;
            String term = raw.trim().replaceAll("\\s+", " ");
            if (term.length() < 2) continue;
            if (term.length() > MAX_TERM_LENGTH) term = term.substring(0, MAX_TERM_LENGTH);
            seen.putIfAbsent(term.toLowerCase(Locale.ROOT), term);
            if (seen.size() >= MAX_TERMS) break;
        }
        return new ArrayList<>(seen.values());
    }
}
