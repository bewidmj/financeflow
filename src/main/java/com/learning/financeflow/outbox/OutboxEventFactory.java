package com.learning.financeflow.outbox;

import com.learning.financeflow.entity.FinancialTransaction;
import com.learning.financeflow.event.TransactionEvent;
import com.learning.financeflow.event.TransactionSnapshot;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class OutboxEventFactory {

    private final ObjectMapper objectMapper;

    public OutboxEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OutboxEvent created(FinancialTransaction transaction) {
        return create("TransactionCreated", transaction, null, TransactionSnapshot.from(transaction),
                transaction.getTransactionVersion());
    }

    public OutboxEvent updated(
            FinancialTransaction transaction, TransactionSnapshot before) {
        return create("TransactionUpdated", transaction, before, TransactionSnapshot.from(transaction),
                transaction.getTransactionVersion());
    }

    public OutboxEvent deleted(
            FinancialTransaction transaction, TransactionSnapshot before) {
        return create("TransactionDeleted", transaction, before, null,
                transaction.getTransactionVersion() + 1);
    }

    private OutboxEvent create(
            String eventType,
            FinancialTransaction transaction,
            TransactionSnapshot before,
            TransactionSnapshot after,
            long transactionVersion) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        TransactionEvent event = new TransactionEvent(
                eventId,
                eventType,
                1,
                transaction.getId(),
                transactionVersion,
                transaction.getOwnerId(),
                before,
                after,
                occurredAt,
                UUID.randomUUID().toString());
        try {
            return new OutboxEvent(
                    eventId,
                    transaction.getId(),
                    transactionVersion,
                    eventType,
                    objectMapper.writeValueAsString(event),
                    occurredAt);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unable to serialize transaction outbox event", exception);
        }
    }
}
