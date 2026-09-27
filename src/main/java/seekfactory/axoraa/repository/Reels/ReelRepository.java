package seekfactory.axoraa.repository.Reels;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.enums.FeedTab;

import java.util.Collection;
import java.util.List;


import org.springframework.data.domain.Pageable;

@Repository
public interface ReelRepository extends JpaRepository<Reel, String> {

    List<Reel> findByFeedTabOrderByCreatedAtDesc(FeedTab feedTab, Pageable pageable);

    List<Reel> findByManufacturerIdOrderByCreatedAtDesc(String manufacturerId);

    /** Buyer-facing: seeks the seller has not paused. */
    List<Reel> findByFeedTabAndListedTrueOrderByCreatedAtDesc(FeedTab feedTab, Pageable pageable);

    List<Reel> findByManufacturerIdAndListedTrueOrderByCreatedAtDesc(String manufacturerId);
    /** "For You": every seek from an approved factory, newest first. */
    @Query("SELECT r FROM Reel r WHERE r.manufacturer.verified = true AND r.listed = true ORDER BY r.createdAt DESC")
    List<Reel> findVisibleFeed(Pageable pageable);

    /** "Following": seeks from approved factories the user follows. */
    @Query("""
            SELECT r FROM Reel r
            WHERE r.manufacturer.verified = true AND r.listed = true
              AND r.manufacturer.id IN (SELECT f.manufacturer.id FROM ManufacturerFollow f WHERE f.user.id = :userId)
            ORDER BY r.createdAt DESC
            """)
    List<Reel> findFollowingFeed(@Param("userId") String userId, Pageable pageable);

    /**
     * Buyer search over seek title, description, hashtags and factory name. A category matches when
     * a tagged product or the factory itself belongs to it.
     */
    @Query("""
            SELECT r FROM Reel r
            WHERE r.manufacturer.verified = true AND r.listed = true
              AND (LOWER(r.title) LIKE :pattern OR LOWER(COALESCE(r.description, '')) LIKE :pattern
                   OR LOWER(r.manufacturer.name) LIKE :pattern
                   OR EXISTS (SELECT 1 FROM Reel r2 JOIN r2.hashtags h WHERE r2 = r AND LOWER(h) LIKE :pattern))
              AND (:allCategories = true
                   OR EXISTS (SELECT 1 FROM Reel r3 JOIN r3.products p WHERE r3 = r AND p.category.id IN :categoryIds)
                   OR EXISTS (SELECT 1 FROM Manufacturer m JOIN m.categories c WHERE m = r.manufacturer AND c.id IN :categoryIds))
            ORDER BY r.createdAt DESC
            """)
    List<Reel> search(@Param("pattern") String pattern,
                      @Param("allCategories") boolean allCategories,
                      @Param("categoryIds") Collection<String> categoryIds,
                      Pageable pageable);

    @Modifying
    @Query("UPDATE Reel r SET r.sharesCount = r.sharesCount + 1 WHERE r.id = :reelId")
    int incrementSharesCount(@Param("reelId") String reelId);

    /** Atomic counter bump so concurrent views never lose updates. */
    @Modifying
    @Query("UPDATE Reel r SET r.viewsCount = r.viewsCount + 1 WHERE r.id = :reelId")
    int incrementViewsCount(@Param("reelId") String reelId);
}
