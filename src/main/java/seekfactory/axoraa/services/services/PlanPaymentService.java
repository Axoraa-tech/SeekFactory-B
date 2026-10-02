package seekfactory.axoraa.services.services;

import org.springframework.web.multipart.MultipartFile;
import seekfactory.axoraa.dto.Response.admin.AdminManagementDtos.Page;
import seekfactory.axoraa.dto.Response.payment.PaymentDtos.MyPayment;
import seekfactory.axoraa.dto.Response.payment.PaymentDtos.PaymentDetail;
import seekfactory.axoraa.dto.Response.payment.PaymentDtos.PaymentRow;
import seekfactory.axoraa.dto.Response.payment.PaymentDtos.Proof;

import java.util.List;

/**
 * Plan payments: the payer uploads proof of an off-platform payment, an admin approves or rejects it,
 * and approval activates the plan.
 */
public interface PlanPaymentService {

    /** Submits a payment for the signed-in user. Region is "india" or "china" and decides the price. */
    MyPayment submit(String userId, String plan, String region, String reference, MultipartFile proof);

    List<MyPayment> listMine(String userId);

    Page<PaymentRow> list(String status, String q, int page, int size);

    PaymentDetail get(String paymentId);

    Proof proof(String paymentId);

    /** Activates the plan and marks the payment approved. Fails unless the payment is pending. */
    void approve(String paymentId, String adminId);

    void reject(String paymentId, String adminId, String reason);
}
