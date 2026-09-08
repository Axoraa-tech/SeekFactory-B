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

    List<Manufacturer> findByVerifiedTrueOrderByFollowerCountDesc();

    List<Manufacturer> findByPremiumTrue();
}