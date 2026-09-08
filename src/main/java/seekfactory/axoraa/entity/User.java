package seekfactory.axoraa.entity;


import jakarta.persistence.*;
import lombok.*;
import seekfactory.axoraa.enums.AuthProvider;
import seekfactory.axoraa.enums.UserRole;


/**
 * Represents a platform user — either a Buyer, Supplier (Manufacturer), or Admin.
 *
 * Authentication supports three providers:
 * - LOCAL: Traditional email + bcrypt password hash
 * - GOOGLE: Google OAuth2 (stores google_id for lookup)
 * - PHONE: SMS OTP verification (future)
 *
 * Maps to the `users` table defined in V1__init_schema.sql.
 */

@Entity
@Table(name = "users", indexes = {@Index(name = "idx_users_email", columnList = "email"), @Index(name = "idx_users_phone", columnList = "phone"),@Index(name = "idx_users_role", columnList = "role")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity{

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "phone", length = 50, unique = true)
    private String phone;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name= "role", length = 32, nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", length = 32, nullable = false)
    private AuthProvider authProvider;

    @Column(name = "google_id", unique = true)
    private String googleId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "industry")
    private String industry;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // ─── Relationships

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Manufacturer manufacturer;
}
