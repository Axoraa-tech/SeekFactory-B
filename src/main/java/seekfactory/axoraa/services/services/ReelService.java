package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.reel.FeedItemResponse;
import seekfactory.axoraa.enums.FeedTab;

import java.util.List;
import java.util.Map;

public interface ReelService {

    List<FeedItemResponse> getFeed(FeedTab tab);

    Map<String, Object> toggleLike(String reelId, String userId);

    Map<String, Object> toggleSave(String reelId, String userId);
}
