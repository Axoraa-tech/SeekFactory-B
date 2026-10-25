package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.product.ProductDetailResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;
import seekfactory.axoraa.dto.Response.product.RecommendedProductsResponse;
import seekfactory.axoraa.services.services.ProductService;
import seekfactory.axoraa.services.services.RecommendationService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.util.List;
import java.util.Map;

/**
 * Public product catalog, with authenticated save/like mutations.
 *
 * The detail endpoint (/products/{slug}) returns:
 * - Full product info with specs (JSONB)
 * - Manufacturer info (for factory badge)
 * - Related products from the same manufacturer
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog and specifications")
public class ProductController {

    private final ProductService productService;
    private final RecommendationService recommendationService;

    @GetMapping("/trending")
    @Operation(summary = "List trending products")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> listTrending(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.of(productService.listTrending(limit, viewerId())));
    }

    @GetMapping("/recommended")
    @Operation(summary = "Products for the viewer: their interests first (activity and recent searches), then trending")
    public ResponseEntity<ApiResponse<RecommendedProductsResponse>> recommended(
            @RequestParam(defaultValue = "60") int limit,
            @RequestParam(required = false) String viewerId,
            @RequestParam(name = "q", required = false) List<String> searchTerms) {
        return ResponseEntity.ok(ApiResponse.of(
                recommendationService.recommend(viewerId(), viewerId, searchTerms, limit)));
    }

    @GetMapping("/catalog")
    @Operation(summary = "Every buyer-visible product in one response (the feed's complete catalogue)")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> catalog() {
        return ResponseEntity.ok(ApiResponse.of(productService.listCatalog(viewerId())));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get product detail by slug (includes manufacturer and related products)")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.of(productService.getBySlug(slug, viewerId())));
    }

    @GetMapping("/category/{categoryId}")
    @Operation(summary = "List products in a category and its subcategories (id or slug)")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> listByCategory(
            @PathVariable String categoryId) {
        return ResponseEntity.ok(ApiResponse.of(productService.listByCategory(categoryId, viewerId())));
    }

    @PostMapping("/{id}/save")
    @Operation(summary = "Toggle save (wishlist) on a product")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleSave(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(productService.toggleSave(id, SecurityUtils.getCurrentUserId())));
    }

    private static String viewerId() {
        return SecurityUtils.findCurrentUserId().orElse(null);
    }

    @PostMapping("/{id}/like")
    @Operation(summary = "Toggle like on a product; returns persisted viewer state and total likes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleLike(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(productService.toggleLike(id, SecurityUtils.getCurrentUserId())));
    }
}
