package seekfactory.axoraa.services.services;

import seekfactory.axoraa.dto.Request.message.MessageSendRequest;
import seekfactory.axoraa.dto.Request.message.StartConversationRequest;
import seekfactory.axoraa.dto.Response.message.ConversationResponse;
import seekfactory.axoraa.dto.Response.message.MessageResponse;

import java.util.List;

public interface ConversationService {

    List<ConversationResponse> listRecent(String userId, int limit);

    ConversationResponse getOrCreateConversation(String userId, StartConversationRequest request);

    List<MessageResponse> getMessages(String conversationId, String userId);

    MessageResponse sendMessage(String conversationId, String userId, MessageSendRequest request);

    void markAsRead(String conversationId, String userId);
}