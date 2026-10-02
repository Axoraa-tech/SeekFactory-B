package seekfactory.axoraa.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import seekfactory.axoraa.dto.Response.common.ApiResponse;
import seekfactory.axoraa.dto.Response.payment.PaymentDtos.MyPayment;
import seekfactory.axoraa.services.services.PlanPaymentService;
import seekfactory.axoraa.utils.SecurityUtils;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Pay for a paid plan by uploading proof of payment")
public class PaymentController {

    private final PlanPaymentService planPaymentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Submit proof of payment for a plan (multipart: file, plan, region=india|china, reference?)")
    public ResponseEntity<ApiResponse<MyPayment>> submit(
            @RequestParam("file") MultipartFile file,
            @RequestParam("plan") String plan,
            @RequestParam("region") String region,
            @RequestParam(value = "reference", required = false) String reference) {
        MyPayment payment = planPaymentService.submit(SecurityUtils.getCurrentUserId(), plan, region, reference, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(payment, "Payment submitted for review"));
    }

    @GetMapping("/mine")
    @Operation(summary = "The signed-in user's own payment requests, newest first")
    public ResponseEntity<ApiResponse<List<MyPayment>>> mine() {
        return ResponseEntity.ok(ApiResponse.of(planPaymentService.listMine(SecurityUtils.getCurrentUserId())));
    }
}
