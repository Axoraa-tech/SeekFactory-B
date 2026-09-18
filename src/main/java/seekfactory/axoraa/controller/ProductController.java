package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.product.ProductDetailResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;
import seekfactory.axoraa.services.services.ProductService;

import java.util.List;

/**
 * Product catalog — ALL PUBLIC.
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

    @GetMapping("/trending")
    @Operation(summary = "List trending products")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> listTrending(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.of(productService.listTrending(limit)));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get product detail by slug (includes manufacturer and related products)")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.of(productService.getBySlug(slug)));
    }

    @GetMapping("/category/{categoryId}")
    @Operation(summary = "List products by category")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> listByCategory(
            @PathVariable String categoryId) {
        return ResponseEntity.ok(ApiResponse.of(productService.listByCategory(categoryId)));
    }
}