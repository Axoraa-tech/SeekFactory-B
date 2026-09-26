package seekfactory.axoraa.entity;

import jakarta.persistence.*;
import lombok.*;
import seekfactory.axoraa.enums.ViewEntityType;

/**
 * One buyer view of a seek (reel) or product. Append-only; powers seller analytics.
 * manufacturerId is denormalised from the viewed entity for cheap per-factory counts.
 */
@Entity
@Table(name = "view_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViewEvent extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 16, nullable = false)
    private ViewEntityType entityType;

    @Column(name = "entity_id", length = 64, nullable = false)
    private String entityId;

    @Column(name = "manufacturer_id", length = 64, nullable = false)
    private String manufacturerId;

    /** "u:&lt;userId&gt;" for signed-in viewers, "a:&lt;anonymous id&gt;" for guests. */
    @Column(name = "viewer_key", length = 128, nullable = false)
    private String viewerKey;
}
