package seekfactory.axoraa.dto.Response.manufacturer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManufacturerResponse {

    private String id;
    private String slug;
    private String name;
    private String logoUrl;
    private String coverUrl;
    private String country;
    private String location;
    private boolean verified;
    private boolean premium;
    private int yearsEstablished;
    private String factorySize;
    private String employees;
    private List<String> exportCountries;
    private String description;
    private int followerCount;
    private List<String> categoryIds;
    private String chairmanName;
}