package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.product.RecommendedProductsResponse;

import java.util.List;

public interface RecommendationService {

    /**
     * Products matched to the viewer's interests (orders, cart, saves, RFQs, product views and
     * recent searches), topped up with trending products. A factory's own products are left out.
     *
     * @param userId            signed-in viewer, or null for a guest
     * @param anonymousViewerId the browser's anonymous view id (guests, and views made before signing in)
     * @param searchTerms       the viewer's recent search terms, newest first
     */
    RecommendedProductsResponse recommend(String userId, String anonymousViewerId, List<String> searchTerms, int limit);
}
