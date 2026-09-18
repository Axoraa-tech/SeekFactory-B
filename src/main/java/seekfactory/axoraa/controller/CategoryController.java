package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Response.category.CategoryResponse;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.services.services.CategoryService;

import java.util.List;

/**
 * Machinery taxonomy categories — ALL PUBLIC.
 *
 * Serves the hierarchical category tree:
 * - /categories         → all categories (flat list)
 * - /categories/roots   → only root-level categories (20 sectors)
 * - /categories/{slug}/children → subcategories under a parent
 * - /categories/{slug}  → single category by slug
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Machinery taxonomy and category tree")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "List all categories (flat)")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.of(categoryService.listAll()));
    }

    @GetMapping("/roots")
    @Operation(summary = "List root categories only")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> listRoots() {
        return ResponseEntity.ok(ApiResponse.of(categoryService.listRoots()));
    }

    @GetMapping("/{slug}/children")
    @Operation(summary = "List subcategories under a parent category")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> listChildren(
            @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.of(categoryService.listChildren(slug)));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get a single category by slug")
    public ResponseEntity<ApiResponse<CategoryResponse>> getBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.of(categoryService.getBySlug(slug)));
    }
}