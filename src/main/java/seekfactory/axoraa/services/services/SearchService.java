package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.search.SearchResponse;

public interface SearchService {

    /**
     * Searches approved products, factories and seeks.
     *
     * @param query    free text; blank matches everything
     * @param category category id or slug, including its subcategories; blank for all
     * @param limit    maximum results per kind
     * @param viewerId signed-in viewer (nullable) for saved / liked state
     */
    SearchResponse search(String query, String category, int limit, String viewerId);
}
