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

    private final java.util.concurrent.Semaphore streamSlots = new java.util.concurrent.Semaphore(1024);
    private final java.util.concurrent.ScheduledExecutorService heartbeat =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "chat-heartbeat"); t.setDaemon(true); return t;
            });

    public SseServiceImpl() {
        heartbeat.scheduleWithFixedDelay(this::heartbeat, 15, 15, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void heartbeat() {
        emitters.forEach((id, watchers) -> watchers.forEach(w -> {
            try { w.emitter().send(SseEmitter.event().comment("keepalive")); }
            catch (IOException | IllegalStateException closed) { removeEmitter(id, w.emitter()); }
        }));
    }

    @jakarta.annotation.PreDestroy
    public void shutdown() {
        heartbeat.shutdownNow();
        emitters.values().forEach(list -> list.forEach(w -> w.emitter().complete()));
        emitters.clear();
    }

    /** A connected stream and the participant who opened it. */
    private record Watcher(String userId, SseEmitter emitter) {}

    // Map conversationId -> connected SSE streams
    private final Map<String, List<Watcher>> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String conversationId, String userId) {
        if (!streamSlots.tryAcquire()) throw new java.util.concurrent.RejectedExecutionException("Too many chat streams");
        // Timeout set to 0 (infinite) or something large like 30 mins
        SseEmitter emitter = new SseEmitter(1800000L); // 30 minutes

        try {
            emitters.compute(conversationId, (id, list) -> {
                if (list == null) list = new CopyOnWriteArrayList<>();
                if (list.size() >= 20) throw new java.util.concurrent.RejectedExecutionException("Too many streams for this chat");
                list.add(new Watcher(userId, emitter));
                return list;
            });
        } catch (RuntimeException rejected) { streamSlots.release(); throw rejected; }

        emitter.onCompletion(() -> removeEmitter(conversationId, emitter));
        emitter.onTimeout(() -> { removeEmitter(conversationId, emitter); emitter.complete(); });
        emitter.onError((e) -> removeEmitter(conversationId, emitter));

        // Send an initial event to establish connection successfully
        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected"));
        } catch (IOException | IllegalStateException e) {
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
                } catch (IOException | IllegalStateException e) {
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
        emitters.computeIfPresent(conversationId, (id, watchers) -> {
            if (watchers.removeIf(w -> w.emitter() == emitter)) streamSlots.release();
            return watchers.isEmpty() ? null : watchers;
        });
    }
}
