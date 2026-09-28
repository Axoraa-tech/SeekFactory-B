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

    @NotBlank(message = "Certificate issuer is required")
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
}
