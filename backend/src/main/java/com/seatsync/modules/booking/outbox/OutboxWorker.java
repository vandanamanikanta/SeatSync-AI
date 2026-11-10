package com.seatsync.modules.booking.outbox;

import com.seatsync.modules.booking.entity.OutboxEvent;
import com.seatsync.modules.booking.entity.OutboxStatus;
import com.seatsync.modules.booking.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled background worker that reliably claims and publishes transactional outbox records.
 * Uses atomic claim tokens, lease timeouts, and exponential backoff.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "seatsync.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxWorker {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxMessageDispatcher messageDispatcher;
    private final TransactionTemplate transactionTemplate;

    @Value("${seatsync.outbox.batch-size:50}")
    private int batchSize = 50;

    @Value("${seatsync.outbox.lease-duration-seconds:30}")
    private int leaseDurationSeconds = 30;

    @Value("${seatsync.outbox.max-retries:5}")
    private int maxRetries = 5;

    @Scheduled(fixedDelayString = "${seatsync.outbox.poll-interval-ms:500}")
    public int processOutboxEvents() {
        String claimToken = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant leaseCutoff = now.minusSeconds(leaseDurationSeconds);

        // Step 1: Atomically claim a batch of eligible records
        List<OutboxEvent> claimedEvents = transactionTemplate.execute(status -> {
            List<Long> candidateIds = outboxEventRepository.findCandidateIdsForClaim(
                    now,
                    leaseCutoff,
                    OutboxStatus.PENDING,
                    OutboxStatus.PROCESSING,
                    PageRequest.of(0, batchSize)
            );

            if (candidateIds.isEmpty()) {
                return List.of();
            }

            int claimedCount = outboxEventRepository.claimEvents(
                    candidateIds,
                    claimToken,
                    now,
                    leaseCutoff,
                    OutboxStatus.PENDING,
                    OutboxStatus.PROCESSING
            );

            if (claimedCount == 0) {
                return List.of();
            }

            return outboxEventRepository.findByClaimToken(claimToken);
        });

        if (claimedEvents == null || claimedEvents.isEmpty()) {
            return 0;
        }

        log.debug("Worker claimed {} outbox events with token {}", claimedEvents.size(), claimToken);

        int publishedCount = 0;

        // Step 2: Dispatch each claimed event outside database transaction locks
        for (OutboxEvent event : claimedEvents) {
            try {
                messageDispatcher.dispatch(event);

                // Step 3: Transition to PUBLISHED only if worker still owns the claim
                final Long eventId = event.getId();
                Boolean marked = transactionTemplate.execute(status -> {
                    int rows = outboxEventRepository.markPublished(
                            eventId,
                            claimToken,
                            OutboxStatus.PUBLISHED,
                            Instant.now()
                    );
                    return rows > 0;
                });

                if (Boolean.TRUE.equals(marked)) {
                    publishedCount++;
                    log.debug("Outbox event {} marked PUBLISHED", event.getEventId());
                } else {
                    log.warn("Lost claim on event {} before marking PUBLISHED. Lease may have expired.", event.getEventId());
                }

            } catch (Exception ex) {
                log.error("Failed to publish outbox event {} (attempt {}): {}",
                        event.getEventId(), event.getRetryCount() + 1, ex.getMessage(), ex);

                handlePublicationFailure(event, claimToken, ex.getMessage());
            }
        }

        return publishedCount;
    }

    private void handlePublicationFailure(OutboxEvent event, String claimToken, String errorMessage) {
        int nextRetry = (event.getRetryCount() != null ? event.getRetryCount() : 0) + 1;
        Instant now = Instant.now();

        transactionTemplate.executeWithoutResult(status -> {
            if (nextRetry >= maxRetries) {
                log.warn("Outbox event {} exceeded max retries ({}). Marking FAILED for manual inspection.",
                        event.getEventId(), maxRetries);
                outboxEventRepository.markFailed(
                        event.getId(),
                        claimToken,
                        OutboxStatus.FAILED,
                        errorMessage,
                        now
                );
            } else {
                long backoffSeconds = (long) Math.min(300, Math.pow(2, nextRetry));
                Instant nextAttemptAt = now.plusSeconds(backoffSeconds);
                log.info("Scheduling retry #{} for outbox event {} at {} (backoff {}s)",
                        nextRetry, event.getEventId(), nextAttemptAt, backoffSeconds);
                outboxEventRepository.markRetry(
                        event.getId(),
                        claimToken,
                        OutboxStatus.PENDING,
                        nextAttemptAt,
                        errorMessage,
                        now
                );
            }
        });
    }
}

