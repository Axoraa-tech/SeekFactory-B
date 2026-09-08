package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Response.message.ConversationResponse;

import java.util.List;

public interface ConversationService {

    List<ConversationResponse> listRecent(String userId, int limit);
}