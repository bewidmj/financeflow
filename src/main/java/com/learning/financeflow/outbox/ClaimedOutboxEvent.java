package com.learning.financeflow.outbox;

import java.util.UUID;

public record ClaimedOutboxEvent(
        UUID id,
        Long aggregateId,
        String eventType,
        String payload,
        int attemptCount,
        UUID claimToken) {
}
