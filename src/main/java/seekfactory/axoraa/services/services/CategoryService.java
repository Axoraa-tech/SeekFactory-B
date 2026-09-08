package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.category.CategoryResponse;

import java.util.List;

public interface CategoryService {

    List<CategoryResponse> listAll();

    List<CategoryResponse> listRoots();

    List<CategoryResponse> listChildren(String parentIdOrSlug);

    CategoryResponse getBySlug(String slug);
}