package seekfactory.axoraa.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Abstract base entity providing common fields for all JPA entities.
 *
 * <ul>
 *   <li><b>id</b> — UUID primary key, auto-generated before persist</li>
 *   <li><b>createdAt</b> — Set once when the entity is first saved</li>
 *   <li><b>updatedAt</b> — Updated automatically on every save</li>
 * </ul>
 *
 * All concrete entities MUST extend this class.
 */

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {


    @Id
    @Column(name = "id", length = 64, nullable = false, updatable = false)
    private String id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;


    /**
     * Generates a UUID-based ID before the entity is persisted for the first time.
     * This ensures IDs are set before any flush/cascade operations.
     */

    @PrePersist
    protected void onCreate() {
        if( this.id == null){
            this.id = UUID.randomUUID().toString();
        }
    }
}
