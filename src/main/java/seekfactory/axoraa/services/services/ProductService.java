package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.product.ProductDetailResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;

import java.util.List;
import java.util.Map;

public interface ProductService {

    List<ProductResponse> listTrending(int limit, String viewerId);

    ProductDetailResponse getBySlug(String slug, String viewerId);

    /** Products in the category or any of its subcategories (id or slug accepted). */
    List<ProductResponse> listByCategory(String categoryIdOrSlug, String viewerId);

    Map<String, Object> toggleSave(String productId, String userId);

    /** Products the user saved, most recently saved first. */
    List<ProductResponse> listSaved(String userId);
}
