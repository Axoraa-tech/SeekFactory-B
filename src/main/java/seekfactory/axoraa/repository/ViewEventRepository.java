package seekfactory.axoraa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.ViewEvent;
import seekfactory.axoraa.enums.ViewEntityType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface ViewEventRepository extends JpaRepository<ViewEvent, String> {

    /** Dedupe check: did this viewer already see this entity since {@code since}? */
    boolean existsByEntityTypeAndEntityIdAndViewerKeyAndCreatedAtAfter(
            ViewEntityType entityType, String entityId, String viewerKey, Instant since);

    /** Views of a factory's reels or products in [from, to). */
    long countByManufacturerIdAndEntityTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            String manufacturerId, ViewEntityType entityType, Instant from, Instant to);

    /** (entityType, createdAt) of a factory's views since {@code since}; bucketed into the stats trend. */
    @Query("SELECT v.entityType, v.createdAt FROM ViewEvent v WHERE v.manufacturerId = :manufacturerId AND v.createdAt >= :since")
    List<Object[]> findTypeAndTimeSince(@Param("manufacturerId") String manufacturerId, @Param("since") Instant since);

    /** (entityId, distinct-viewer views) rows for the given entities; entities with none are absent. */
    @Query("SELECT v.entityId, COUNT(v) FROM ViewEvent v WHERE v.entityType = :type AND v.entityId IN :ids GROUP BY v.entityId")
    List<Object[]> countByEntityIds(@Param("type") ViewEntityType type, @Param("ids") Collection<String> ids);
}
