package seekfactory.axoraa.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A buyer following a manufacturer. Drives the "Following" feed tab and the factory's follower count.
 */
@Entity
@Table(name = "manufacturer_follows", uniqueConstraints = {@UniqueConstraint(columnNames = {"manufacturer_id", "user_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManufacturerFollow extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturer_id", nullable = false)
    private Manufacturer manufacturer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
