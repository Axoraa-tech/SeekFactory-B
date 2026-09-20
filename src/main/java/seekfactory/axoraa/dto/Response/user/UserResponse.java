package seekfactory.axoraa.dto.Response.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private String id;
    private String name;
    private String email;
    private String role;        // "Buyer" or "Supplier" — mapped from UserRole enum
    private String avatarUrl;
    private String companyName;
    private String industry;
    private String country;
    private String phone;
}