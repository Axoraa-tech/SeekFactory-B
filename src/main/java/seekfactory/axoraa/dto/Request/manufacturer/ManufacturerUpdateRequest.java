package seekfactory.axoraa.dto.Request.manufacturer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import seekfactory.axoraa.dto.common.FactoryCertificate;
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

    /** http(s) URL; an empty string clears it. */
    @Size(max = 500)
    private String websiteUrl;

    @Size(max = 64)
    private String annualTurnover;

    @Min(value = 0, message = "Production lines cannot be negative")
    @Max(value = 1000, message = "Production lines looks too large")
    private Integer productionLines;

    @Min(value = 1900, message = "Year founded must be 1900 or later")
    private Integer yearsEstablished;

    @Size(max = 20, message = "At most 20 certifications")
    private List<@NotBlank @Size(max = 120) String> certifications;

    /** Full certificate list (replaces the stored one). */
    @Size(max = 30, message = "At most 30 certificates")
    private List<@Valid FactoryCertificate> certificates;
}