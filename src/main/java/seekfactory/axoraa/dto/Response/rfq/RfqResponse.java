package seekfactory.axoraa.dto.Response.rfq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RfqResponse {

    private String id;
    private String referenceNumber;
    private String productName;
    private String categoryId;
    private String quantity;
    private String unit;
    private String targetPrice;
    private String currency;
    private String incoterm;
    private String companyName;
    private String details;
    private String attachmentName;
    private String attachmentSize;
    private String attachmentUrl;
    private String status;
    private String createdAt;
}