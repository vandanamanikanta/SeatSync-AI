package com.seatsync.modules.booking.repository;

import com.seatsync.modules.booking.entity.OutboxEvent;
import com.seatsync.modules.booking.entity.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    Optional<OutboxEvent> findByEventId(String eventId);

    List<OutboxEvent> findByStatus(OutboxStatus status);

    List<OutboxEvent> findByClaimToken(String claimToken);

    @Query("SELECT o.id FROM OutboxEvent o " +
           "WHERE (o.status = :pendingStatus AND o.nextAttemptAt <= :now) " +
           "   OR (o.status = :processingStatus AND o.claimedAt < :leaseCutoff) " +
           "ORDER BY o.nextAttemptAt ASC, o.id ASC")
    List<Long> findCandidateIdsForClaim(
            @Param("now") Instant now,
            @Param("leaseCutoff") Instant leaseCutoff,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("processingStatus") OutboxStatus processingStatus,
            Pageable pageable
    );

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.status = :processingStatus, o.claimToken = :claimToken, o.claimedAt = :now " +
           "WHERE o.id IN (:ids) AND (o.status = :pendingStatus OR (o.status = :processingStatus AND o.claimedAt < :leaseCutoff))")
    int claimEvents(
            @Param("ids") List<Long> ids,
            @Param("claimToken") String claimToken,
            @Param("now") Instant now,
            @Param("leaseCutoff") Instant leaseCutoff,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("processingStatus") OutboxStatus processingStatus
    );

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.status = :publishedStatus, o.claimToken = NULL, o.claimedAt = NULL, o.updatedAt = :now " +
           "WHERE o.id = :id AND o.claimToken = :claimToken")
    int markPublished(
            @Param("id") Long id,
            @Param("claimToken") String claimToken,
            @Param("publishedStatus") OutboxStatus publishedStatus,
            @Param("now") Instant now
    );

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.status = :pendingStatus, o.retryCount = o.retryCount + 1, o.claimToken = NULL, o.claimedAt = NULL, " +
           "o.nextAttemptAt = :nextAttemptAt, o.lastErrorMessage = :errorMessage, o.updatedAt = :now " +
           "WHERE o.id = :id AND o.claimToken = :claimToken")
    int markRetry(
            @Param("id") Long id,
            @Param("claimToken") String claimToken,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("errorMessage") String errorMessage,
            @Param("now") Instant now
    );

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.status = :failedStatus, o.retryCount = o.retryCount + 1, o.claimToken = NULL, o.claimedAt = NULL, " +
           "o.lastErrorMessage = :errorMessage, o.updatedAt = :now " +
           "WHERE o.id = :id AND o.claimToken = :claimToken")
    int markFailed(
            @Param("id") Long id,
            @Param("claimToken") String claimToken,
            @Param("failedStatus") OutboxStatus failedStatus,
            @Param("errorMessage") String errorMessage,
            @Param("now") Instant now
    );
}
