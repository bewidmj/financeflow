package com.learning.financeflow.outbox;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxEventClaimer claimer;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final String topic;

    public OutboxPublisher(
            OutboxEventClaimer claimer,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry,
            @Value("${app.kafka.transaction-topic:financeflow.transactions.v1}") String topic) {
        this.claimer = claimer;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:1000}")
    public void publishPending() {
        List<ClaimedOutboxEvent> events = claimer.claim(BATCH_SIZE);
        for (ClaimedOutboxEvent event : events) {
            try {
                kafkaTemplate.send(topic, event.aggregateId().toString(), event.payload())
                        .get(30, TimeUnit.SECONDS);
                boolean marked = claimer.markPublished(event.id(), event.claimToken(), Instant.now());
                if (!marked) {
                    logger.warn("Outbox publish acknowledgment could not be recorded for eventId={}",
                            event.id());
                } else {
                    meterRegistry.counter("finance.outbox.published").increment();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                markFailed(event, exception);
                return;
            } catch (ExecutionException | TimeoutException | RuntimeException exception) {
                markFailed(event, exception);
            }
        }
    }

    private void markFailed(ClaimedOutboxEvent event, Exception exception) {
        claimer.markFailed(
                event.id(),
                event.claimToken(),
                event.attemptCount(),
                exception.getMessage());
        meterRegistry.counter("finance.outbox.publish.failures").increment();
        logger.error("Kafka publish failed for outbox eventId={}, attempt={}",
                event.id(), event.attemptCount(), exception);
    }
}
