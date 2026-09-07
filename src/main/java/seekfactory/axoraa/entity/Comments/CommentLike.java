package seekfactory.axoraa.entity.Comments;


import jakarta.persistence.*;
import lombok.*;
import seekfactory.axoraa.entity.BaseEntity;
import seekfactory.axoraa.entity.User;

/**
 * Tracks a user's "like" on a comment.
 */

@Entity
@Table(name = "comment_likes", uniqueConstraints = { @UniqueConstraint(columnNames = {"comment_id", "user_id"})})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CommentLike extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
