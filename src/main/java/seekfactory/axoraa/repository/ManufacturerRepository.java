package seekfactory.axoraa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.Manufacturer;

import java.util.List;
import java.util.Optional;

@Repository
public interface ManufacturerRepository extends JpaRepository<Manufacturer, String> {

    Optional<Manufacturer> findBySlug(String slug);

    Optional<Manufacturer> findByUserId(String userId);

    long countByVerifiedTrue();

    List<Manufacturer> findByVerifiedTrueOrderByFollowerCountDesc();

    /** Buyer-facing listing: approved factories only, filtered in SQL. */
    List<Manufacturer> findByVerifiedTrue();

    List<Manufacturer> findByPremiumTrue();
}