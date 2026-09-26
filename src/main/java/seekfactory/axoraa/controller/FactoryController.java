package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Request.manufacturer.ManufacturerUpdateRequest;
import seekfactory.axoraa.dto.Request.product.ProductCreateRequest;
import seekfactory.axoraa.dto.Request.reel.ReelCreateRequest;
import seekfactory.axoraa.dto.Request.rfq.RfqQuoteRequest;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.factory.FactoryStatsResponse;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;
import seekfactory.axoraa.dto.Response.product.ProductResponse;
import seekfactory.axoraa.dto.Response.reel.ReelResponse;
import seekfactory.axoraa.dto.Response.rfq.RfqResponse;
import seekfactory.axoraa.services.services.FactoryService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.util.List;

/**
 * Manufacturer / Factory Workspace — ROLE_SUPPLIER only.
 * SecurityConfig enforces the same rule; @PreAuthorize is defense-in-depth.
 */
@RestController
@RequestMapping("/api/v1/factory")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_SUPPLIER')")
@Tag(name = "Factory Hub", description = "Manufacturer seller portal operations")
public class FactoryController {

    private final FactoryService factoryService;

    @GetMapping("/profile")
    @Operation(summary = "Get current supplier factory profile")
    public ResponseEntity<ApiResponse<ManufacturerResponse>> getProfile() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(factoryService.getProfile(userId)));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update supplier factory profile")
    public ResponseEntity<ApiResponse<ManufacturerResponse>> updateProfile(
            @Valid @RequestBody ManufacturerUpdateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(factoryService.updateProfile(userId, request), "Factory profile updated"));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get factory analytics and metrics")
    public ResponseEntity<ApiResponse<FactoryStatsResponse>> getStats() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(factoryService.getStats(userId)));
    }

    @GetMapping("/products")
    @Operation(summary = "Get products listed by current factory")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProducts() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(factoryService.getProducts(userId)));
    }

    @PostMapping("/products")
    @Operation(summary = "Upload a new product to factory catalog")
    public ResponseEntity<ApiResponse<ProductResponse>> addProduct(
            @Valid @RequestBody ProductCreateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        ProductResponse response = factoryService.addProduct(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Product added successfully"));
    }

    @DeleteMapping("/products/{id}")
    @Operation(summary = "Delete or archive a factory product")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        factoryService.deleteProduct(userId, id);
        return ResponseEntity.ok(ApiResponse.ok("Product deleted"));
    }

    @GetMapping("/seeks")
    @Operation(summary = "Get video reels uploaded by current factory")
    public ResponseEntity<ApiResponse<List<ReelResponse>>> getSeeks() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(factoryService.getSeeks(userId)));
    }

    @PostMapping("/seeks")
    @Operation(summary = "Upload a new video seek / reel")
    public ResponseEntity<ApiResponse<ReelResponse>> addSeek(
            @Valid @RequestBody ReelCreateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        ReelResponse response = factoryService.addSeek(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Video seek uploaded"));
    }

    @DeleteMapping("/seeks/{id}")
    @Operation(summary = "Delete a factory video seek")
    public ResponseEntity<ApiResponse<Void>> deleteSeek(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        factoryService.deleteSeek(userId, id);
        return ResponseEntity.ok(ApiResponse.ok("Reel deleted"));
    }

    @GetMapping("/rfqs")
    @Operation(summary = "Get incoming RFQs matching factory capabilities")
    public ResponseEntity<ApiResponse<List<RfqResponse>>> getRfqs() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(factoryService.getRfqs(userId)));
    }

    @PostMapping("/rfqs/{id}/quote")
    @Operation(summary = "Submit a quotation for an RFQ")
    public ResponseEntity<ApiResponse<Void>> submitQuote(
            @PathVariable String id,
            @Valid @RequestBody RfqQuoteRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        factoryService.submitQuote(userId, id, request);
        return ResponseEntity.ok(ApiResponse.ok("Quotation submitted successfully"));
    }
}