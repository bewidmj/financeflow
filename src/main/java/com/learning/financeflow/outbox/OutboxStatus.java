package com.learning.financeflow.outbox;

public enum OutboxStatus {
    PENDING,
    IN_FLIGHT,
    RETRY,
    PUBLISHED,
    FAILED
}
