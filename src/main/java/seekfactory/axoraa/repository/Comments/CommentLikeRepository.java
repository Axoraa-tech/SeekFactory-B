// CommentLikeRepository.java
package seekfactory.axoraa.repository.Comments;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Comments.CommentLike;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, String> {

    Optional<CommentLike> findByCommentIdAndUserId(String commentId, String userId);

    @Query("SELECT l.comment.id FROM CommentLike l WHERE l.user.id = :userId AND l.comment.reel.id = :reelId")
    List<String> findLikedCommentIdsOnReel(@Param("userId") String userId,
                                                     @Param("reelId") String reelId);
}