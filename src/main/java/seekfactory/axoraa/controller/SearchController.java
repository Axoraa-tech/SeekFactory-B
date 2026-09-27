package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.search.SearchResponse;
import seekfactory.axoraa.services.services.SearchService;
import seekfactory.axoraa.utils.SecurityUtils;

/**
 * Buyer search — PUBLIC.
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Search products, factories and seeks")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @Operation(summary = "Search products, manufacturers and seeks (category = id or slug, includes subcategories)")
    public ResponseEntity<ApiResponse<SearchResponse>> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "30") int limit) {
        String viewerId = SecurityUtils.findCurrentUserId().orElse(null);
        return ResponseEntity.ok(ApiResponse.of(searchService.search(q, category, limit, viewerId)));
    }
}
