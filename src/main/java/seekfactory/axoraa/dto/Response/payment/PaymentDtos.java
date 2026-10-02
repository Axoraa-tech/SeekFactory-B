package seekfactory.axoraa.dto.Response.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Shapes for plan payments. The proof bytes are never part of these records: they are streamed
 * from their own endpoint so lists stay small.
 */
public final class PaymentDtos {

    private PaymentDtos() {}

    /** What the payer sees about their own request. */
    public record MyPayment(
            String id, String planName, String planCode, String currency, BigDecimal amount,
            String status, String rejectionReason, Instant createdAt, Instant reviewedAt) {}

    /** One row of the admin payments list. */
    public record PaymentRow(
            String id, String accountType, String planName, String currency, BigDecimal amount,
            String status, String payerName, String payerEmail, String payerCompany, String payerCountry,
            Instant createdAt) {}

    /** Everything the admin needs to decide: the payer's details as submitted plus the proof metadata. */
    public record PaymentDetail(
            String id, String accountType, String planName, String planCode, String region, String currency,
            BigDecimal amount, String payerReference, String status,
            String userId, String payerName, String payerEmail, String payerPhone, String payerCompany,
            String payerCountry, String payerAddress,
            String manufacturerId, String manufacturerName,
            String proofContentType, String proofFilename, int proofSize,
            String reviewedBy, Instant reviewedAt, String rejectionReason, Instant createdAt) {}

    public record RejectRequest(@NotBlank @Size(max = 500) String reason) {}

    /** The proof file with its stored type, for the streaming endpoint. */
    public record Proof(byte[] data, String contentType, String filename) {}
}
