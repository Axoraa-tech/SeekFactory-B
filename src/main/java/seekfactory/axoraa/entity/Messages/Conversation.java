package seekfactory.axoraa.entity.Messages;

import jakarta.persistence.*;
import lombok.*;
import seekfactory.axoraa.entity.BaseEntity;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.User;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A B2B chat thread between a buyer and a manufacturer.
 * Unique constraint ensures only one conversation per buyer-manufacturer pair.
 */
@Entity
@Table(name = "conversations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"buyer_id", "manufacturer_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturer_id", nullable = false)
    private Manufacturer manufacturer;

    @Column(name = "last_message_text", columnDefinition = "TEXT")
    private String lastMessageText;

    @Column(name = "last_message_at")
    private OffsetDateTime lastMessageAt;

    @Column(name = "unread_count_buyer", nullable = false)
    @Builder.Default
    private Integer unreadCountBuyer = 0;

    @Column(name = "unread_count_supplier", nullable = false)
    @Builder.Default
    private Integer unreadCountSupplier = 0;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Message> messages = new ArrayList<>();
}