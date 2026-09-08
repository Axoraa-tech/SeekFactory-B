package seekfactory.axoraa.utils;

import java.util.UUID;

/**
 * Centralized ID generation utility.
 * Currently uses UUID v4. Can be swapped to ULID/TSID for time-sorted IDs.
 */
public final class IdGenerator {

    private IdGenerator() {}

    public static String generateId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generates an RFQ reference number like "RFQ-2026-000142".
     */
    public static String generateRfqReference(int year, int sequence) {
        return String.format("RFQ-%d-%06d", year, sequence);
    }
}