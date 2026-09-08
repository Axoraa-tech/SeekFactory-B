// CommentRepository.java
package seekfactory.axoraa.repository.Comments;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Comments.Comment;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, String> {

    List<seekfactory.axoraa.entity.Comments.Comment> findByReelIdAndParentIsNullOrderByCreatedAtDesc(String reelId);
}