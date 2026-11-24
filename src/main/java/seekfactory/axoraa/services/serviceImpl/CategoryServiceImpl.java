package seekfactory.axoraa.services.serviceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seekfactory.axoraa.dto.Response.category.CategoryResponse;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.exceptions.BadRequestException;
import seekfactory.axoraa.exceptions.ResourceNotFoundException;
import seekfactory.axoraa.repository.CategoryRepository;
import seekfactory.axoraa.repository.ProductRepository;
import seekfactory.axoraa.services.services.CategoryService;

import java.util.*;

/**
 * Manages the hierarchical machinery taxonomy.
 *
 * Categories are organized in a tree:
 * - Root categories (parentId = null): "Agriculture", "Machine Tools", "Energy", etc.
 * - Subcategories: "Harvesters" under "Agriculture", "CNC Lathes" under "Machine Tools"
 *
 * The listChildren method accepts either a parent ID or a parent slug,
 * matching both frontends' CategoryRepository.listChildren(parentIdOrSlug).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ModelMapper modelMapper;
    private final ProductRepository productRepository;

    // Taxonomy may be extended outside this process. Read the small tree on each
    // request instead of retaining a stale per-instance cache after an addition.
    @Override
    public List<CategoryResponse> listAll() {
        List<Category> categories = categoryRepository.findAll();
        return mapResponses(categories, categories);
    }

    @Override
    public List<CategoryResponse> listRoots() {
        return mapResponses(categoryRepository.findByParentIsNullOrderByNameAsc(), categoryRepository.findAll());
    }

    @Override
    public List<CategoryResponse> listChildren(String parentIdOrSlug) {
        // Resolve parent category by ID or slug
        Category parent = categoryRepository.findById(parentIdOrSlug)
                .or(() -> categoryRepository.findBySlug(parentIdOrSlug))
                .orElse(null);

        if (parent == null) {
            return List.of();
        }

        List<Category> children = categoryRepository.findByParentIdOrderByNameAsc(parent.getId());
        return mapResponses(children, categoryRepository.findAll());
    }

    @Override
    public CategoryResponse getBySlug(String slugOrId) {
        Category category = categoryRepository.findBySlug(slugOrId)
                .or(() -> categoryRepository.findById(slugOrId))
                .orElseThrow(() -> new ResourceNotFoundException("Category", "slug or id", slugOrId));
        return mapResponses(List.of(category), categoryRepository.findAll()).get(0);
    }

    @Override
    @Transactional
    public List<CategoryResponse> reorder(List<String> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) throw new BadRequestException("No categories to order");
        if (new HashSet<>(orderedIds).size() != orderedIds.size()) throw new BadRequestException("A category is listed twice");
        Map<String, Category> byId = new HashMap<>();
        categoryRepository.findAllById(orderedIds).forEach(c -> byId.put(c.getId(), c));
        if (byId.size() != orderedIds.size()) throw new BadRequestException("Unknown category in the order");

        String parentId = parentIdOf(byId.get(orderedIds.get(0)));
        for (Category c : byId.values()) {
            if (!Objects.equals(parentIdOf(c), parentId)) {
                throw new BadRequestException("Only categories of the same level can be ordered together");
            }
        }
        // Siblings left out of the list lose their old position, so the saved order is exactly what was sent
        List<Category> all = categoryRepository.findAll();
        for (Category c : all) {
            if (Objects.equals(parentIdOf(c), parentId) && !byId.containsKey(c.getId())) c.setDisplayOrder(null);
        }
        for (int i = 0; i < orderedIds.size(); i++) byId.get(orderedIds.get(i)).setDisplayOrder(i);
        categoryRepository.saveAll(all);
        log.info("Category order updated ({} categories under {})", orderedIds.size(), parentId == null ? "root" : parentId);
        return mapResponses(orderedIds.stream().map(byId::get).toList(), all);
    }

    private static String parentIdOf(Category c) {
        return c.getParent() == null ? null : c.getParent().getId();
    }

    // ─── Private Helpers ──────────────────────────────────────

    /**
     * Count public products once in their own category and each ancestor, matching
     * buyer filtering by a category and its descendants. Never use seeded listingCount.
     * One grouped product query per response, not a query per category.
     */
    private List<CategoryResponse> mapResponses(List<Category> selected, List<Category> taxonomy) {
        Map<String, Category> byId = new HashMap<>();
        taxonomy.forEach(category -> byId.put(category.getId(), category));
        Map<String, Long> counts = new HashMap<>();
        for (ProductRepository.CategoryListingCount row : productRepository.countVisibleByCategory()) {
            String id = row.getCategoryId();
            Set<String> visited = new HashSet<>();
            while (id != null && visited.add(id)) {
                counts.merge(id, row.getListingCount(), Long::sum);
                Category category = byId.get(id);
                id = category != null && category.getParent() != null ? category.getParent().getId() : null;
            }
        }
        return selected.stream().map(category -> mapToResponse(category, counts)).toList();
    }

    private CategoryResponse mapToResponse(Category category, Map<String, Long> counts) {
        CategoryResponse response = modelMapper.map(category, CategoryResponse.class);
        // Map parent relationship to simple parentId string
        response.setParentId(category.getParent() != null ? category.getParent().getId() : null);
        response.setListingCount((int) Math.min(Integer.MAX_VALUE, counts.getOrDefault(category.getId(), 0L)));
        return response;
    }
}
