package seekfactory.axoraa.repository.Reels;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.enums.FeedTab;

import java.util.List;


import org.springframework.data.domain.Pageable;

@Repository
public interface ReelRepository extends JpaRepository<Reel, String> {

    List<Reel> findByFeedTabOrderByCreatedAtDesc(FeedTab feedTab, Pageable pageable);

    List<Reel> findByManufacturerIdOrderByCreatedAtDesc(String manufacturerId);

    /** Atomic counter bump so concurrent views never lose updates. */
    @Modifying
    @Query("UPDATE Reel r SET r.viewsCount = r.viewsCount + 1 WHERE r.id = :reelId")
    int incrementViewsCount(@Param("reelId") String reelId);
}
