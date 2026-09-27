// ReelLikeRepository.java
package seekfactory.axoraa.repository.Reels;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Reels.ReelLike;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Collection;

@Repository
public interface ReelLikeRepository extends JpaRepository<ReelLike, String> {

    Optional<seekfactory.axoraa.entity.Reels.ReelLike> findByReelIdAndUserId(String reelId, String userId);

    boolean existsByReelIdAndUserId(String reelId, String userId);

    @Query("SELECT l.reel.id FROM ReelLike l WHERE l.user.id = :userId AND l.reel.id IN :reelIds")
    List<String> findLikedReelIds(@Param("userId") String userId,
                                            @Param("reelIds") Collection<String> reelIds);
}