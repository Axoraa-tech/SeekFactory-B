package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerDetailResponse;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.services.services.ManufacturerService;

import java.util.List;

/**
 * Manufacturer / Factory profiles — ALL PUBLIC.
 *
 * The detail endpoint (/manufacturers/{slug}) returns a composite response
 * with the factory profile, its products, and its video reels.
 */
@RestController
@RequestMapping("/api/v1/manufacturers")
@RequiredArgsConstructor
@Tag(name = "Manufacturers", description = "Factory profiles and verified suppliers")
public class ManufacturerController {

    private final ManufacturerService manufacturerService;

    @GetMapping
    @Operation(summary = "List all manufacturers")
    public ResponseEntity<ApiResponse<List<ManufacturerResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.of(manufacturerService.listAll()));
    }

    @GetMapping("/verified")
    @Operation(summary = "List verified manufacturers (sorted by follower count)")
    public ResponseEntity<ApiResponse<List<ManufacturerResponse>>> listVerified(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.of(manufacturerService.listVerified(limit)));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get manufacturer detail by slug (includes products and reels)")
    public ResponseEntity<ApiResponse<ManufacturerDetailResponse>> getBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.of(manufacturerService.getBySlug(slug)));
    }
}