package seekfactory.axoraa.dto.Response.plan;

import java.time.Instant;

/**
 * Admin queue row. {@code productCount}/{@code productLimit} are live values;
 * {@code countAtRequest}/{@code limitAtRequest} are what the factory had when it asked.
 */
public record AdminProductLimitRequestRow(
        String id, String manufacturerId, String manufacturerName, String manufacturerSlug,
        String ownerName, String ownerEmail,
        int productCount, int productLimit, int countAtRequest, int limitAtRequest, int requestedLimit,
        String message, String status, String adminNote, String reviewedBy, Instant reviewedAt, Instant createdAt) {}
