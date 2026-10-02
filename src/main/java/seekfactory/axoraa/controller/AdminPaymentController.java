package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.Page;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.payment.PaymentDtos.PaymentDetail;
import seekfactory.axoraa.dto.Response.payment.PaymentDtos.PaymentRow;
import seekfactory.axoraa.dto.Response.payment.PaymentDtos.Proof;
import seekfactory.axoraa.dto.Response.payment.PaymentDtos.RejectRequest;
import seekfactory.axoraa.services.services.PlanPaymentService;
import seekfactory.axoraa.utils.SecurityUtils;

@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin payments", description = "Review plan payments (ROLE_ADMIN only)")
public class AdminPaymentController {

    private final PlanPaymentService planPaymentService;

    @GetMapping
    @Operation(summary = "Page through plan payments, pending first")
    public ResponseEntity<ApiResponse<Page<PaymentRow>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(planPaymentService.list(status, q, page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One payment with the payer's details")
    public ResponseEntity<ApiResponse<PaymentDetail>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(planPaymentService.get(id)));
    }

    /** Never cached or shared: the proof is a financial document. */
    @GetMapping("/{id}/proof")
    @Operation(summary = "The uploaded payment screenshot or invoice")
    public ResponseEntity<byte[]> proof(@PathVariable String id) {
        Proof proof = planPaymentService.proof(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(proof.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("Content-Disposition", "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(proof.data());
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "Approve a payment and activate the plan")
    public ResponseEntity<ApiResponse<Void>> approve(@PathVariable String id) {
        planPaymentService.approve(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Payment approved and plan activated"));
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Reject a payment with a reason the payer will see")
    public ResponseEntity<ApiResponse<Void>> reject(@PathVariable String id, @Valid @RequestBody RejectRequest request) {
        planPaymentService.reject(id, SecurityUtils.getCurrentUserId(), request.reason());
        return ResponseEntity.ok(ApiResponse.ok("Payment rejected"));
    }
}
