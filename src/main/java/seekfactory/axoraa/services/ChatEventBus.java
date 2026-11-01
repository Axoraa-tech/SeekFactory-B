package seekfactory.axoraa.services;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.*;

/** PostgreSQL transactional pub/sub. Payloads contain IDs only; messages stay in the database. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatEventBus {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final ApplicationEventPublisher events;
    /** Transaction-pooling proxies cannot preserve LISTEN session state. Use a direct/session URL. */
    @org.springframework.beans.factory.annotation.Value("${app.chat.listener-url:}")
    private String listenerUrl = "";
    @org.springframework.beans.factory.annotation.Value("${app.chat.listener-username:${spring.datasource.username:}}")
    private String listenerUsername = "";
    @org.springframework.beans.factory.annotation.Value("${app.chat.listener-password:${spring.datasource.password:}}")
    private String listenerPassword = "";
    private volatile boolean running;
    private volatile Connection connection;
    private volatile boolean listening;
    public boolean isListening() { return listening; }
    private Thread listener;
    private final ExecutorService delivery = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(256), r -> {
                Thread t = new Thread(r, "chat-event-delivery"); t.setDaemon(true); return t;
            });

    public record Committed(String conversationId, String messageId) {}

    /** Runs on the sending transaction's connection: PostgreSQL delivers only after COMMIT. */
    public void publish(String conversationId, String messageId) {
        jdbc.queryForObject("SELECT pg_notify('seekfactory_chat', ?)", String.class,
                conversationId + ":" + messageId);
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (running) return;
        running = true;
        listener = new Thread(this::listen, "chat-postgres-listener");
        listener.setDaemon(true);
        listener.start();
    }

    private void listen() {
        while (running) {
            try (Connection c = listenerUrl.isBlank() ? dataSource.getConnection()
                    : java.sql.DriverManager.getConnection(listenerUrl, listenerUsername, listenerPassword)) {
                connection = c;
                c.setAutoCommit(true);
                try (var statement = c.createStatement()) { statement.execute("LISTEN seekfactory_chat"); }
                listening = true;
                PGConnection pg = c.unwrap(PGConnection.class);
                while (running) {
                    var notifications = pg.getNotifications(1000);
                    if (notifications == null) continue;
                    for (var notification : notifications) {
                        String[] ids = notification.getParameter().split(":", -1);
                        if (ids.length != 2 || !ids[0].matches("[A-Za-z0-9-]{1,64}")
                                || !ids[1].matches("[A-Za-z0-9-]{1,64}")) continue;
                        try { delivery.execute(() -> events.publishEvent(new Committed(ids[0], ids[1]))); }
                        catch (RejectedExecutionException busy) {
                            log.warn("Chat delivery queue full; clients recover through database sync");
                        }
                    }
                }
            } catch (Exception failure) {
                if (running) {
                    log.warn("Chat listener disconnected; reconnecting ({})", failure.getClass().getSimpleName());
                    try { Thread.sleep(1000); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                }
            } finally { listening = false; connection = null; }
        }
    }

    @PreDestroy
    public void close() {
        running = false;
        if (listener != null) listener.interrupt();
        try { if (connection != null) connection.close(); } catch (Exception ignored) { }
        delivery.shutdownNow();
    }
}
