package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import seekfactory.axoraa.dto.Request.order.OrderContactRequest;
import seekfactory.axoraa.dto.Request.rfq.RfqCreateRequest;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.rfq.RfqResponse;
import seekfactory.axoraa.services.services.RfqService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.util.List;
import java.util.Map;

/**
 * Request for Quotation management — AUTHENTICATED only.
 *
 * Buyers submit RFQs and can view their own RFQ history.
 * The submit endpoint returns { ok: true, id: "..." } to match
 * the frontend RfqRepository.submit() contract.
 */
@RestController
@RequestMapping("/api/v1/rfqs")
@RequiredArgsConstructor
@Tag(name = "RFQ", description = "Request for Quotation management")
public class RfqController {

    private final RfqService rfqService;

    @PostMapping
    @Operation(summary = "Submit a new RFQ")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submit(
            @Valid @RequestBody RfqCreateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        RfqResponse rfq = rfqService.submit(userId, request);

        // Return format matching frontend contract: { ok: true, id: "..." }
        Map<String, Object> result = Map.of(
                "ok", true,
                "id", rfq.getId(),
                "referenceNumber", rfq.getReferenceNumber()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of(result, "RFQ submitted successfully"));
    }

    @GetMapping
    @Operation(summary = "List current user's RFQs")
    public ResponseEntity<ApiResponse<List<RfqResponse>>> listMyRfqs() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(rfqService.listByUser(userId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of the current user's RFQs with all factory quotes")
    public ResponseEntity<ApiResponse<RfqResponse>> getMine(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(rfqService.getMine(SecurityUtils.getCurrentUserId(), id)));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel an open RFQ")
    public ResponseEntity<ApiResponse<RfqResponse>> cancel(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(rfqService.cancel(SecurityUtils.getCurrentUserId(), id), "RFQ cancelled"));
    }

    @PostMapping("/{id}/quotes/{quoteId}/accept")
    @Operation(summary = "Accept a quote and place an order with that factory (body: delivery details; items are ignored)")
    public ResponseEntity<ApiResponse<RfqResponse>> acceptQuote(
            @PathVariable String id,
            @PathVariable String quoteId,
            @Valid @RequestBody OrderContactRequest contact) {
        return ResponseEntity.ok(ApiResponse.of(
                rfqService.acceptQuote(SecurityUtils.getCurrentUserId(), id, quoteId, contact), "Quote accepted"));
    }

    @PostMapping("/{id}/quotes/{quoteId}/reject")
    @Operation(summary = "Decline a quote")
    public ResponseEntity<ApiResponse<RfqResponse>> rejectQuote(@PathVariable String id, @PathVariable String quoteId) {
        return ResponseEntity.ok(ApiResponse.of(
                rfqService.rejectQuote(SecurityUtils.getCurrentUserId(), id, quoteId), "Quote declined"));
    }
}
