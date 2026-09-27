// ReelSaveRepository.java
package seekfactory.axoraa.repository.Reels;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Reels.ReelSave;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Collection;

@Repository
public interface ReelSaveRepository extends JpaRepository<ReelSave, String> {

    Optional<seekfactory.axoraa.entity.Reels.ReelSave> findByReelIdAndUserId(String reelId, String userId);

    boolean existsByReelIdAndUserId(String reelId, String userId);

    List<ReelSave> findByUserIdOrderByCreatedAtDesc(String userId);

    @Query("SELECT s.reel.id FROM ReelSave s WHERE s.user.id = :userId AND s.reel.id IN :reelIds")
    List<String> findSavedReelIds(@Param("userId") String userId,
                                            @Param("reelIds") Collection<String> reelIds);
}