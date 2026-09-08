// ReelSaveRepository.java
package seekfactory.axoraa.repository.Reels;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Reels.ReelSave;

import java.util.Optional;

@Repository
public interface ReelSaveRepository extends JpaRepository<ReelSave, String> {

    Optional<seekfactory.axoraa.entity.Reels.ReelSave> findByReelIdAndUserId(String reelId, String userId);

    boolean existsByReelIdAndUserId(String reelId, String userId);
}