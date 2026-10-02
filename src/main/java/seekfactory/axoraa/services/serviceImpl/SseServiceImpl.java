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

    /** A connected stream and the participant who opened it. */
    private record Watcher(String userId, SseEmitter emitter) {}

    // Map conversationId -> connected SSE streams
    private final Map<String, List<Watcher>> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String conversationId, String userId) {
        // Timeout set to 0 (infinite) or something large like 30 mins
        SseEmitter emitter = new SseEmitter(1800000L); // 30 minutes

        emitters.computeIfAbsent(conversationId, k -> new CopyOnWriteArrayList<>()).add(new Watcher(userId, emitter));

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
        List<Watcher> conversationEmitters = emitters.get(conversationId);
        if (conversationEmitters != null) {
            for (Watcher watcher : conversationEmitters) {
                try {
                    watcher.emitter().send(SseEmitter.event()
                            .name("message")
                            .data(message));
                } catch (IOException e) {
                    removeEmitter(conversationId, watcher.emitter());
                }
            }
        }
    }

    @Override
    public boolean isWatching(String conversationId, String userId) {
        List<Watcher> watchers = emitters.get(conversationId);
        return userId != null && watchers != null && watchers.stream().anyMatch(w -> userId.equals(w.userId()));
    }

    private void removeEmitter(String conversationId, SseEmitter emitter) {
        List<Watcher> conversationEmitters = emitters.get(conversationId);
        if (conversationEmitters != null) {
            conversationEmitters.removeIf(w -> w.emitter() == emitter);
            if (conversationEmitters.isEmpty()) {
                emitters.remove(conversationId);
            }
        }
    }
}
