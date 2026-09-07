package seekfactory.axoraa.entity.Reels;


import jakarta.persistence.*;
import lombok.*;
import seekfactory.axoraa.entity.BaseEntity;
import seekfactory.axoraa.entity.User;

/**
 * Tracks a user's "like" on a reel. Unique constraint prevents double-liking.
 */
@Entity
@Table(name = "reel_like", uniqueConstraints = {@UniqueConstraint(columnNames = {"reel_id", "user_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReelLike extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reel_id", nullable = false)
    private Reel reel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
