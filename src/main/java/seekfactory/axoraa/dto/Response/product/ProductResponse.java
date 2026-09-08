package seekfactory.axoraa.dto.Response.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private String id;
    private String slug;
    private String manufacturerId;
    private String name;
    private String imageUrl;
    private String description;
    private BigDecimal priceInr;
    private String unit;
    private String moq;
    private String categoryId;
    private Map<String, String> specs;
}