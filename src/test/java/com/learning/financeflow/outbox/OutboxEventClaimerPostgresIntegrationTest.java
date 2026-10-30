package com.learning.financeflow.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class OutboxEventClaimerPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private OutboxEventClaimer claimer;

    @BeforeEach
    void clearOutbox() {
        outboxRepository.deleteAll();
    }

    @Test
    void claimsPerTransactionInVersionOrderAndReleasesNextAfterAcknowledgment() {
        Long transactionId = 100L;
        OutboxEvent firstVersionToPublish = event(transactionId, 1);
        OutboxEvent blockedVersion = event(transactionId, 2);
        outboxRepository.saveAll(List.of(
                firstVersionToPublish,
                blockedVersion,
                event(200L, 1)));

        List<ClaimedOutboxEvent> firstBatch = claimer.claim(10);

        assertEquals(2, firstBatch.size());
        assertTrue(firstBatch.stream().anyMatch(event -> event.aggregateId().equals(200L)));
        ClaimedOutboxEvent firstVersion = firstBatch.stream()
                .filter(event -> event.id().equals(firstVersionToPublish.getId()))
                .findFirst()
                .orElseThrow();
        assertFalse(firstBatch.stream().anyMatch(
                event -> event.id().equals(blockedVersion.getId())));
        assertEquals(OutboxStatus.PENDING,
                outboxRepository.findById(blockedVersion.getId()).orElseThrow().getStatus());

        assertTrue(claimer.markPublished(
                firstVersion.id(), firstVersion.claimToken(), Instant.now()));
        List<ClaimedOutboxEvent> nextBatch = claimer.claim(10);
        assertEquals(1, nextBatch.size());
        assertEquals(transactionId, nextBatch.getFirst().aggregateId());
        assertEquals(2, outboxRepository.findById(nextBatch.getFirst().id())
                .orElseThrow().getAggregateVersion());
    }

    @Test
    void concurrentClaimersCannotClaimOneOutboxEventTwice() throws Exception {
        outboxRepository.save(event(300L, 1));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<List<ClaimedOutboxEvent>> first = executor.submit(() -> claimAfterStart(ready, start));
            Future<List<ClaimedOutboxEvent>> second = executor.submit(() -> claimAfterStart(ready, start));
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            List<ClaimedOutboxEvent> all = new ArrayList<>();
            all.addAll(first.get(20, java.util.concurrent.TimeUnit.SECONDS));
            all.addAll(second.get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, all.size());
        } finally {
            executor.shutdownNow();
        }
    }

    private List<ClaimedOutboxEvent> claimAfterStart(
            CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return claimer.claim(1);
    }

    private OutboxEvent event(Long transactionId, long version) {
        return new OutboxEvent(
                UUID.randomUUID(),
                transactionId,
                version,
                "TransactionCreated",
                "{}",
                Instant.now());
    }
}
