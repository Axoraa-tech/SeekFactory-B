package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.product.ProductDetailResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;

import java.util.List;

public interface ProductService {

    List<ProductResponse> listTrending(int limit);

    ProductDetailResponse getBySlug(String slug);

    List<ProductResponse> listByCategory(String categoryId);
}