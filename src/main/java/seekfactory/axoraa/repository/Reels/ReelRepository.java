package seekfactory.axoraa.repository.Reels;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Reels.Reel;
import seekfactory.axoraa.enums.FeedTab;

import java.util.List;


@Repository
public interface ReelRepository extends JpaRepository<Reel, String> {

    List<Reel> findByFeedTabOrderByCreatedAtDesc(FeedTab feedTab);

    List<Reel> findByManufacturerIdOrderByCreatedAtDesc(String manufacturerId);
}
