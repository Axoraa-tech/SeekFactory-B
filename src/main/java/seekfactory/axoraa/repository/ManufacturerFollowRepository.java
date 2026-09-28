package seekfactory.axoraa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.ManufacturerFollow;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ManufacturerFollowRepository extends JpaRepository<ManufacturerFollow, String> {

    Optional<ManufacturerFollow> findByManufacturerIdAndUserId(String manufacturerId, String userId);

    boolean existsByManufacturerIdAndUserId(String manufacturerId, String userId);

    List<ManufacturerFollow> findByUserIdOrderByCreatedAtDesc(String userId);

    @Query("SELECT f.manufacturer.id FROM ManufacturerFollow f WHERE f.user.id = :userId AND f.manufacturer.id IN :manufacturerIds")
    List<String> findFollowedManufacturerIds(@Param("userId") String userId,
                                             @Param("manufacturerIds") Collection<String> manufacturerIds);

    long countByManufacturerId(String manufacturerId);
}
