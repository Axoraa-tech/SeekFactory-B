package seekfactory.axoraa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.ViewEvent;
import seekfactory.axoraa.enums.ViewEntityType;

import java.time.Instant;

@Repository
public interface ViewEventRepository extends JpaRepository<ViewEvent, String> {

    /** Dedupe check: did this viewer already see this entity since {@code since}? */
    boolean existsByEntityTypeAndEntityIdAndViewerKeyAndCreatedAtAfter(
            ViewEntityType entityType, String entityId, String viewerKey, Instant since);

    /** Views of a factory's reels or products in [from, to). */
    long countByManufacturerIdAndEntityTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            String manufacturerId, ViewEntityType entityType, Instant from, Instant to);
}
