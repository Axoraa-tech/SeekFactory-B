package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.reel.FeedItemResponse;
import seekfactory.axoraa.enums.FeedTab;

import java.util.List;

public interface ReelService {

    List<FeedItemResponse> getFeed(FeedTab tab);
}