package seekfactory.axoraa.services.services;

/**
 * Records buyer views of seeks and products for seller analytics.
 */
public interface AnalyticsService {

    /**
     * Record a seek (reel) impression.
     *
     * @param anonymousViewerId client-generated id used to dedupe guests; ignored when signed in
     * @return true if a new view was counted (false for duplicates, owners, or unidentifiable guests)
     */
    boolean recordReelView(String reelId, String anonymousViewerId);

    /** Record a product detail page view. Same semantics as {@link #recordReelView}. */
    boolean recordProductView(String productId, String anonymousViewerId);
}
