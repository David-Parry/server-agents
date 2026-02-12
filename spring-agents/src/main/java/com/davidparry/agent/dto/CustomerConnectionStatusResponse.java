package com.davidparry.agent.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for customer WebSocket connection status.
 */
public record CustomerConnectionStatusResponse(
    UUID customerId,
    String customerName,
    boolean connected,
    int activeConnectionCount,
    int totalActiveSessions,
    List<ConnectionDetail> connections
) {
    /**
     * Details about an individual connection.
     */
    public record ConnectionDetail(
        String connectionId,
        Instant connectedAt,
        Instant lastActivityAt,
        int activeSessions,
        long totalSessionsCreated,
        long totalToolCalls,
        long connectionDurationMs,
        long idleTimeMs
    ) {}
}
