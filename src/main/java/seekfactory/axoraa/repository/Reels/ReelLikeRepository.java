// ReelLikeRepository.java
package seekfactory.axoraa.repository.Reels;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Reels.ReelLike;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReelLikeRepository extends JpaRepository<ReelLike, String> {

    Optional<seekfactory.axoraa.entity.Reels.ReelLike> findByReelIdAndUserId(String reelId, String userId);

    boolean existsByReelIdAndUserId(String reelId, String userId);

    @Query("SELECT l.reel.id FROM ReelLike l WHERE l.user.id = :userId AND l.reel.id IN :reelIds")
    List<String> findLikedReelIds(@Param("userId") String userId,
                                            @Param("reelIds") Collection<String> reelIds);

    /** (reelId, count) rows for the given reels; reels with none are absent. */
    @Query("SELECT l.reel.id, COUNT(l) FROM ReelLike l WHERE l.reel.id IN :reelIds GROUP BY l.reel.id")
    List<Object[]> countByReelIds(@Param("reelIds") Collection<String> reelIds);
}
