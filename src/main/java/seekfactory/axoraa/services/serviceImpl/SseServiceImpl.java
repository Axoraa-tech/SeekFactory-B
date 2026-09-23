package seekfactory.axoraa.services.serviceImpl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import seekfactory.axoraa.dto.Response.message.MessageResponse;
import seekfactory.axoraa.services.services.SseService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class SseServiceImpl implements SseService {

    // Map conversationId -> List of connected SSE emitters
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String conversationId) {
        // Timeout set to 0 (infinite) or something large like 30 mins
        SseEmitter emitter = new SseEmitter(1800000L); // 30 minutes

        emitters.computeIfAbsent(conversationId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(conversationId, emitter));
        emitter.onTimeout(() -> removeEmitter(conversationId, emitter));
        emitter.onError((e) -> removeEmitter(conversationId, emitter));

        // Send an initial event to establish connection successfully
        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected"));
        } catch (IOException e) {
            removeEmitter(conversationId, emitter);
        }

        return emitter;
    }

    @Override
    public void pushMessageToConversation(String conversationId, MessageResponse message) {
        List<SseEmitter> conversationEmitters = emitters.get(conversationId);
        if (conversationEmitters != null) {
            for (SseEmitter emitter : conversationEmitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(message));
                } catch (IOException e) {
                    removeEmitter(conversationId, emitter);
                }
            }
        }
    }

    private void removeEmitter(String conversationId, SseEmitter emitter) {
        List<SseEmitter> conversationEmitters = emitters.get(conversationId);
        if (conversationEmitters != null) {
            conversationEmitters.remove(emitter);
            if (conversationEmitters.isEmpty()) {
                emitters.remove(conversationId);
            }
        }
    }
}
