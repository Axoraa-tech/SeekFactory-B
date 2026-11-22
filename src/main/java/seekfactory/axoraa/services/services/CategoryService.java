package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.category.CategoryResponse;

import java.util.List;

public interface CategoryService {

    List<CategoryResponse> listAll();

    List<CategoryResponse> listRoots();

    List<CategoryResponse> listChildren(String parentIdOrSlug);

    CategoryResponse getBySlug(String slug);

    /**
     * Puts sibling categories (all roots, or all children of one parent) in the given order:
     * the first id is shown first. Categories of that level left out keep no position and follow.
     */
    List<CategoryResponse> reorder(List<String> orderedIds);
}
