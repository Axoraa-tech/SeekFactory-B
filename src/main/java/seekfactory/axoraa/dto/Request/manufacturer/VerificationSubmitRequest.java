package seekfactory.axoraa.dto.Request.manufacturer;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationSubmitRequest {

    @NotBlank(message = "Company registration number is required")
    @Size(max = 120)
    private String companyRegNumber;

    @Size(max = 120)
    private String taxId;

    @Size(max = 40)
    private String registrationDate;

    /** Country the factory is registered in; updates the profile when given. */
    @Size(max = 100)
    private String country;

    @NotBlank(message = "Factory address is required")
    @Size(max = 1000)
    private String factoryAddress;

    @Size(max = 20, message = "At most 20 certifications")
    private List<@NotBlank @Size(max = 120) String> certifications;
}
