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
    private Boolean emailVerified;
    private String taxId;
    private String address;
    /** Buyer membership tier in lower case: free, pro, enterprise. */
    private String plan;
    /** ISO timestamp of account creation. */
    private String memberSince;
}