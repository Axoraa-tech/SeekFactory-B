package seekfactory.axoraa.dto.Request.user;

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
}