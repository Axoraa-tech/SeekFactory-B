package seekfactory.axoraa.dto.Request.rfq;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RfqCreateRequest {

    @NotBlank(message = "Product name is required")
    private String productName;

    @NotBlank(message = "Quantity is required")
    private String quantity;

    @NotBlank(message = "Details are required")
    private String details;

    @NotBlank(message = "Company name is required")
    private String companyName;

    private String categoryId;
    private String unit;
    private String targetPrice;
    private String currency;     // "INR", "USD", etc.
    private String incoterm;     // "FOB", "CIF", etc.
    private String attachmentName;
    private String attachmentSize;
    private String attachmentUrl;
}