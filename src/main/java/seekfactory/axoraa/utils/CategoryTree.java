package seekfactory.axoraa.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import seekfactory.axoraa.entity.Category;
import seekfactory.axoraa.repository.CategoryRepository;

import java.util.*;

/**
 * Category hierarchy lookups. The taxonomy is small (~200 rows), so it is read whole.
 */
@Component
@RequiredArgsConstructor
public class CategoryTree {

    private final CategoryRepository categoryRepository;

    /** Resolves a category by id or slug; empty when neither matches. */
    public Optional<Category> resolve(String idOrSlug) {
        if (idOrSlug == null || idOrSlug.isBlank()) return Optional.empty();
        return categoryRepository.findById(idOrSlug).or(() -> categoryRepository.findBySlug(idOrSlug));
    }

    /** The category's id plus every descendant id. */
    public Set<String> withDescendants(String categoryId) {
        Map<String, List<String>> childrenByParent = new HashMap<>();
        for (Category c : categoryRepository.findAll()) {
            if (c.getParent() != null) {
                childrenByParent.computeIfAbsent(c.getParent().getId(), k -> new ArrayList<>()).add(c.getId());
            }
        }
        Set<String> result = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(List.of(categoryId));
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (result.add(id)) {
                queue.addAll(childrenByParent.getOrDefault(id, List.of()));
            }
        }
        return result;
    }

    /** The given categories' ids plus all their ancestors' ids. */
    public Set<String> withAncestors(Collection<Category> categories) {
        Set<String> result = new LinkedHashSet<>();
        for (Category c : categories) {
            Category current = c;
            while (current != null && result.add(current.getId())) {
                current = current.getParent();
            }
        }
        return result;
    }
}
