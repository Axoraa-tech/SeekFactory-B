// ReelLikeRepository.java
package seekfactory.axoraa.repository.Reels;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Reels.ReelLike;

import java.util.Optional;

@Repository
public interface ReelLikeRepository extends JpaRepository<ReelLike, String> {

    Optional<seekfactory.axoraa.entity.Reels.ReelLike> findByReelIdAndUserId(String reelId, String userId);

    boolean existsByReelIdAndUserId(String reelId, String userId);
}