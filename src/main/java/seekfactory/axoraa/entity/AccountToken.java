package seekfactory.axoraa.entity;

import jakarta.persistence.*;
import lombok.*;
import seekfactory.axoraa.enums.AccountTokenPurpose;

import java.time.Instant;

/**
 * A single-use token sent by email (password reset, email verification).
 * Only the SHA-256 hash of the token is stored.
 */
@Entity
@Table(name = "account_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", length = 32, nullable = false)
    private AccountTokenPurpose purpose;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }
}
