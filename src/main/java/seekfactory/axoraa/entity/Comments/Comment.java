package seekfactory.axoraa.entity.Comments;


import jakarta.persistence.*;
import lombok.*;
import seekfactory.axoraa.entity.BaseEntity;
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.entity.User;

import java.util.ArrayList;
import java.util.List;

/**
 * A comment on a reel. Supports threading via self-referencing parent_id.
 *
 * Top-level comments have parent = null.
 * Replies have parent pointing to the top-level comment.
 *
 * Author metadata is denormalized (authorName, authorAvatarUrl, etc.)
 * for performance — avoids joining User table on every comment render.
 */


@Entity
@Table(name = "comments", indexes = {@Index(name = "idx_comments_reel_id", columnList = "reel_id"), @Index(name = "idx_comments_parent_id", columnList = "parent_id")})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Comment extends BaseEntity {


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reel_id", nullable = false)
    private Reel reel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Comment> replies = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Column(name = "author_avatar_url", columnDefinition = "TEXT")
    private String authorAvatarUrl;

    @Column(name = "author_company")
    private String authorCompany;

    @Column(name = "author_country", length = 100)
    private String authorCountry;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "likes_count", nullable = false)
    @Builder.Default
    private Integer likesCount = 0;
}
