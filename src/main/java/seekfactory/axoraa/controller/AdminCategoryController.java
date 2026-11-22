package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import seekfactory.axoraa.dto.Response.category.CategoryResponse;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.services.services.CategoryService;

import java.util.List;

/** Admin control over how categories are presented to buyers. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/categories")
@Tag(name = "Admin categories")
public class AdminCategoryController {

    private final CategoryService categoryService;

    public record CategoryOrderRequest(List<String> ids) {}

    @PutMapping("/order")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Set the display order of sibling categories (first id is shown first)")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> reorder(@RequestBody CategoryOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.of(categoryService.reorder(request.ids()), "Category order saved"));
    }
}
