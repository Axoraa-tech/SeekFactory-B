package seekfactory.axoraa.dto.Response.plan;

import java.time.Instant;

/** One limit request as the manufacturer sees it. */
public record ProductLimitRequestResponse(
        String id, int currentCount, int currentLimit, int requestedLimit, String message,
        String status, String adminNote, Instant createdAt, Instant reviewedAt) {}
