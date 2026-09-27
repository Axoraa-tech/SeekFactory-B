package seekfactory.axoraa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import seekfactory.axoraa.entity.OrderRequest;
import seekfactory.axoraa.enums.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRequestRepository extends JpaRepository<OrderRequest, String> {

    List<OrderRequest> findByManufacturerIdOrderByCreatedAtDesc(String manufacturerId);

    List<OrderRequest> findByBuyerIdOrderByCreatedAtDesc(String buyerId);

    List<OrderRequest> findByBuyerIdAndManufacturerIdOrderByCreatedAtDesc(String buyerId, String manufacturerId);

    boolean existsByReferenceNumber(String referenceNumber);

    /** Double-submit guard: a matching fresh pending request from the same buyer. */
    Optional<OrderRequest> findFirstByBuyerIdAndProductIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            String buyerId, String productId, OrderStatus status, Instant since);
}
