package seekfactory.axoraa.dto.Response.manufacturer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** The seller's own verification application. Never exposed publicly (holds tax/registration ids). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResponse {

    private String status;           // PENDING | APPROVED | REJECTED
    private boolean submitted;
    private String submittedAt;
    private String reviewedAt;
    private String rejectionReason;
    private String companyRegNumber;
    private String taxId;
    private String registrationDate;
    private String factoryAddress;
    private List<String> certifications;
}
