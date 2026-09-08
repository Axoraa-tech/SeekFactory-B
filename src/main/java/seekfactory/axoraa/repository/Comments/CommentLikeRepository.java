// CommentLikeRepository.java
package seekfactory.axoraa.repository.Comments;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Comments.CommentLike;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, String> {

    boolean existsByCommentIdAndUserId(String commentId, String userId);
}