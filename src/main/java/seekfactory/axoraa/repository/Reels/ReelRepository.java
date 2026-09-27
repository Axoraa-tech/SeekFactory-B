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

    /**
     * The buyer feed: approved factories only, with the manufacturer joined in.
     *
     * The approval filter is in SQL rather than applied after loading, so an
     * unapproved factory's seeks never leave the database. Joining the
     * manufacturer removes one lazy load per reel, which dominated the response
     * time when the database is a network hop away. Products stay lazy and are
     * batched by `default_batch_fetch_size`.
     */
    @Query("""
            SELECT r FROM Reel r
            JOIN FETCH r.manufacturer m
            WHERE r.feedTab = :feedTab AND m.verified = true
            ORDER BY r.createdAt DESC
            """)
    List<Reel> findApprovedByFeedTab(@Param("feedTab") FeedTab feedTab, Pageable pageable);

    List<Reel> findByManufacturerIdOrderByCreatedAtDesc(String manufacturerId);

    /** Atomic counter bump so concurrent views never lose updates. */
    @Modifying
    @Query("UPDATE Reel r SET r.viewsCount = r.viewsCount + 1 WHERE r.id = :reelId")
    int incrementViewsCount(@Param("reelId") String reelId);
}
