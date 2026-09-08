package seekfactory.axoraa.dto.Response.product;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import seekfactory.axoraa.dto.Response.manufacturer.ManufacturerResponse;


import java.util.List;

/**
 * Product detail — includes the manufacturing factory info and related products.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailResponse {

    private ProductResponse product;
    private ManufacturerResponse manufacturer;
    private List<ProductResponse> related;
}