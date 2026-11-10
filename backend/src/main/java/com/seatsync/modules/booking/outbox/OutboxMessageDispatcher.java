package com.seatsync.modules.booking.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatsync.modules.booking.entity.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Dispatches transactional outbox events to downstream destinations,
 * including real-time WebSocket STOMP topics.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxMessageDispatcher {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Dispatch an outbox event. Throws exception on failure so caller can retry.
     */
    public void dispatch(OutboxEvent outboxEvent) throws Exception {
        String eventType = outboxEvent.getEventType();
        String payloadJson = outboxEvent.getPayload();

        JsonNode root = objectMapper.readTree(payloadJson);
        if (root.isTextual()) {
            root = objectMapper.readTree(root.asText());
        }
        Long eventId = root.hasNonNull("eventId") ? root.get("eventId").asLong() : null;

        if (eventId == null) {
            log.warn("Outbox event {} ({}) has no eventId. Skipping WebSocket broadcast.",
                    outboxEvent.getEventId(), eventType);
            return;
        }

        List<Long> seatIds = new ArrayList<>();
        if (root.has("seatIds") && root.get("seatIds").isArray()) {
            for (JsonNode n : root.get("seatIds")) {
                seatIds.add(n.asLong());
            }
        }

        String targetStatus;
        switch (eventType) {
            case "SEAT_HELD" -> targetStatus = "HELD";
            case "SEAT_RELEASED" -> targetStatus = "AVAILABLE";
            case "BOOKING_CONFIRMED" -> targetStatus = "BOOKED";
            default -> {
                log.debug("Outbox event type {} is not mapped to a seat status transition. Broadcast omitted.", eventType);
                return;
            }
        }

        Long primarySeatId = seatIds.isEmpty() ? null : seatIds.getFirst();
        SeatUpdateMessage updateMessage = SeatUpdateMessage.builder()
                .messageId(outboxEvent.getEventId())
                .eventId(eventId)
                .seatId(primarySeatId)
                .seatIds(seatIds)
                .status(targetStatus)
                .timestamp(Instant.now())
                .build();

        String destination = "/topic/events/" + eventId + "/seats";
        messagingTemplate.convertAndSend(destination, updateMessage);

        log.info("Successfully broadcast outbox event {} ({}) to {} for {} seats",
                outboxEvent.getEventId(), eventType, destination, seatIds.size());
    }
}

