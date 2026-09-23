package seekfactory.axoraa.services.services;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import seekfactory.axoraa.dto.Response.message.MessageResponse;

public interface SseService {
    SseEmitter subscribe(String conversationId);
    void pushMessageToConversation(String conversationId, MessageResponse message);
}
