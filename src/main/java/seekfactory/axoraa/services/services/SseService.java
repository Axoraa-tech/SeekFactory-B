package seekfactory.axoraa.services.services;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import seekfactory.axoraa.dto.Response.message.MessageResponse;

public interface SseService {
    /** Opens the live message stream of a conversation for one of its participants. */
    SseEmitter subscribe(String conversationId, String userId);

    void pushMessageToConversation(String conversationId, MessageResponse message);

    /** True while the user has this conversation open (its live stream is connected). */
    boolean isWatching(String conversationId, String userId);
}
