// CommentRepository.java
package seekfactory.axoraa.repository.Comments;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Comments.Comment;

import java.util.Collection;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, String> {

    List<seekfactory.axoraa.entity.Comments.Comment> findByReelIdAndParentIsNullOrderByCreatedAtDesc(String reelId);

    /** (reelId, count) rows for the given reels; reels with none are absent. */
    @Query("SELECT c.reel.id, COUNT(c) FROM Comment c WHERE c.reel.id IN :reelIds GROUP BY c.reel.id")
    List<Object[]> countByReelIds(@Param("reelIds") Collection<String> reelIds);
}
