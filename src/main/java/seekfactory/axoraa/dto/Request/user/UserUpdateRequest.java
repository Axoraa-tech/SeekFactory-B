package seekfactory.axoraa.dto.Request.user;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    private String name;
    private String companyName;
    private String industry;
    private String country;
    private String phone;
    private String avatarUrl;

    @Size(max = 120, message = "Tax ID must be at most 120 characters")
    private String taxId;

    @Size(max = 1000, message = "Address must be at most 1000 characters")
    private String address;
}