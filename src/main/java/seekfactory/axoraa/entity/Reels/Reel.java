package seekfactory.axoraa.entity.Reels;


import jakarta.persistence.*;
import lombok.*;
import seekfactory.axoraa.entity.BaseEntity;
import seekfactory.axoraa.entity.Manufacturer;
import seekfactory.axoraa.entity.Product;
import seekfactory.axoraa.enums.FeedTab;

import java.util.HashSet;
import java.util.Set;

/**
 * A short-form industrial video showcase — the core discovery mechanism.
 *
 * Reels appear in the vertical-scrolling feed (like TikTok/Instagram Reels)
 * and are the primary way buyers discover manufacturing capabilities.
 *
 * Each reel belongs to a manufacturer and can feature multiple products.
 * Hashtags stored as @ElementCollection for simple tag storage.
 */

@Entity
@Table(name = "reels", indexes = { @Index(name = "idx_reels_manufacturer_id", columnList = "manufacturer_id"), @Index(name = "idx_reels_feed_tab", columnList = "feed_tab")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturer_id", nullable = false)
    private Manufacturer manufacturer;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "poster_url", nullable = false, columnDefinition = "TEXT")
    private String posterUrl;

    @Column(name = "video_url", columnDefinition = "TEXT")
    private String videoUrl;

    @Column(name = "duration_sec", nullable = false)
    @Builder.Default
    private Integer durationSec = 0;

    @Column(name = "start_sec", nullable = false)
    @Builder.Default
    private Integer startSec = 0;


    @Column(name = "views_count", nullable = false)
    @Builder.Default
    private Long viewsCount = 0L;

    @Column(name = "likes_count", nullable = false)
    @Builder.Default
    private Integer likesCount = 0;

    @Column(name = "comments_count", nullable = false)
    @Builder.Default
    private Integer commentsCount = 0;

    @Column(name = "shares_count", nullable = false)
    @Builder.Default
    private Integer sharesCount = 0;

    @Column(name = "saves_count", nullable = false)
    @Builder.Default
    private Integer savesCount = 0;


    @Enumerated(EnumType.STRING)
    @Column(name = "feed_tab", length = 32, nullable = false)
    @Builder.Default
    private FeedTab feedTab = FeedTab.FOR_YOU;

    // ─── Hashtags (ElementCollection)

    @ElementCollection
    @CollectionTable(
            name = "reel_hashtags",
            joinColumns = @JoinColumn(name = "reel_id")
    )
    @Column(name = "hashtag", nullable = false, length = 100)
    @Builder.Default
    private Set<String> hashtags = new HashSet<>();

    // ─── Featured Products (ManyToMany)

    @ManyToMany
    @JoinTable(name = "reel_products", joinColumns = @JoinColumn(name = "reel_id"), inverseJoinColumns = @JoinColumn(name = "product_id"))
    @Builder.Default
    private Set<Product> products = new HashSet<>();
}
