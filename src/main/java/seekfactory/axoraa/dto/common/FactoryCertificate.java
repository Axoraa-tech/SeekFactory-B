package seekfactory.axoraa.dto.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A certificate document on a factory profile (stored as JSON in manufacturers.certificates).
 * Used as-is in requests and responses; `verified` is only ever set by SeekFactory, never
 * accepted from the seller.
 *
 * <p>{@code custom} marks a certificate the company named itself (e.g. "ISO 13485:2016") instead
 * of picking a preset. Its document (PDF or image scan) must be a file uploaded through
 * SeekFactory's media storage, and the issuer is optional. It is shown as company-provided,
 * never as verified, unless SeekFactory verifies it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactoryCertificate {

    @Size(max = 64)
    private String id;

    @NotBlank(message = "Certificate title is required")
    @Size(max = 200)
    private String title;

    /** Required for preset certificates; optional for custom ones (checked in the service). */
    @Size(max = 200)
    private String issuer;

    @Size(max = 120)
    private String certNumber;

    @Size(max = 20)
    private String issueDate;

    @Size(max = 20)
    private String expiryDate;

    @NotBlank(message = "Certificate image is required")
    private String imageUrl;

    @Size(max = 40)
    private String category;

    private boolean verified;

    /** Company-entered certificate name with an uploaded document (see class docs). */
    private boolean custom;

    /** Original file name of the uploaded document, for display. */
    @Size(max = 255)
    private String documentName;
}
