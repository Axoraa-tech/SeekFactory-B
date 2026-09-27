package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.reel.FeedItemResponse;
import seekfactory.axoraa.enums.FeedTab;

import java.util.List;
import java.util.Map;

public interface ReelService {

    /**
     * For You: every seek from an approved factory. Following: seeks from factories the viewer follows
     * (always empty for guests). viewerId may be null.
     */
    List<FeedItemResponse> getFeed(FeedTab tab, String viewerId, int page, int size);

    Map<String, Object> toggleLike(String reelId, String userId);

    Map<String, Object> toggleSave(String reelId, String userId);

    /** Counts a share (link copy / native share). Guests count too. */
    Map<String, Object> recordShare(String reelId);

    /** Seeks the user saved, most recently saved first. */
    List<FeedItemResponse> listSaved(String userId);
}
