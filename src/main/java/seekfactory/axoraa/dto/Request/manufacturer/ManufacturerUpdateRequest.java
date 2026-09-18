package seekfactory.axoraa.dto.Request.manufacturer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManufacturerUpdateRequest {

    private String name;
    private String logoUrl;
    private String coverUrl;
    private String location;
    private String factorySize;
    private String employees;
    private String description;
    private String chairmanName;
    private List<String> exportCountries;
    private List<String> categoryIds;
}