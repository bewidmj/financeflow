package com.learning.financeflow.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OutboxEventClaimer {

    private static final int MAX_ATTEMPTS = 12;

    private final JdbcTemplate jdbcTemplate;

    public OutboxEventClaimer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<ClaimedOutboxEvent> claim(int batchSize) {
        jdbcTemplate.update("""
                UPDATE finance_transaction_outbox
                SET status = 'FAILED',
                    last_error = COALESCE(last_error, 'Claim lease expired after maximum attempts'),
                    claimed_until = NULL,
                    claim_token = NULL
                WHERE status = 'IN_FLIGHT'
                  AND claimed_until < CURRENT_TIMESTAMP
                  AND attempt_count >= ?
                """, MAX_ATTEMPTS);

        UUID claimToken = UUID.randomUUID();
        return jdbcTemplate.query("""
                UPDATE finance_transaction_outbox AS outbox
                SET status = 'IN_FLIGHT',
                    claim_token = ?,
                    claimed_until = CURRENT_TIMESTAMP + INTERVAL '60 seconds',
                    attempt_count = attempt_count + 1
                WHERE outbox.id IN (
                    SELECT candidate.id
                    FROM finance_transaction_outbox candidate
                    WHERE ((
                        candidate.status IN ('PENDING', 'RETRY')
                        AND candidate.next_attempt_at <= CURRENT_TIMESTAMP
                    ) OR (
                        candidate.status = 'IN_FLIGHT'
                        AND candidate.claimed_until < CURRENT_TIMESTAMP
                        AND candidate.attempt_count < ?
                    ))
                    AND NOT EXISTS (
                        SELECT 1
                        FROM finance_transaction_outbox earlier
                        WHERE earlier.aggregate_id = candidate.aggregate_id
                          AND (earlier.aggregate_version, earlier.id)
                              < (candidate.aggregate_version, candidate.id)
                          AND earlier.status <> 'PUBLISHED'
                    )
                    ORDER BY candidate.created_at, candidate.aggregate_id,
                             candidate.aggregate_version, candidate.id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                RETURNING outbox.id, outbox.aggregate_id, outbox.event_type,
                          outbox.payload, outbox.attempt_count
                """,
                (rs, rowNum) -> mapClaimed(rs, claimToken),
                claimToken,
                MAX_ATTEMPTS,
                batchSize);
    }

    @Transactional
    public boolean markPublished(UUID id, UUID claimToken, Instant publishedAt) {
        return jdbcTemplate.update("""
                UPDATE finance_transaction_outbox
                SET status = 'PUBLISHED',
                    published_at = ?,
                    claimed_until = NULL,
                    claim_token = NULL,
                    last_error = NULL
                WHERE id = ? AND status = 'IN_FLIGHT' AND claim_token = ?
                """, Timestamp.from(publishedAt), id, claimToken) == 1;
    }

    @Transactional
    public void markFailed(UUID id, UUID claimToken, int attemptCount, String error) {
        boolean exhausted = attemptCount >= MAX_ATTEMPTS;
        long delaySeconds = Math.min(3600, 1L << Math.min(attemptCount - 1, 12));
        jdbcTemplate.update("""
                UPDATE finance_transaction_outbox
                SET status = ?,
                    next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                    claimed_until = NULL,
                    claim_token = NULL,
                    last_error = ?
                WHERE id = ? AND status = 'IN_FLIGHT' AND claim_token = ?
                """,
                exhausted ? "FAILED" : "RETRY",
                delaySeconds,
                truncate(error, 4000),
                id,
                claimToken);
    }

    private ClaimedOutboxEvent mapClaimed(ResultSet rs, UUID claimToken) throws SQLException {
        return new ClaimedOutboxEvent(
                rs.getObject("id", UUID.class),
                rs.getLong("aggregate_id"),
                rs.getString("event_type"),
                rs.getString("payload"),
                rs.getInt("attempt_count"),
                claimToken);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "Unknown Kafka publishing failure";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
