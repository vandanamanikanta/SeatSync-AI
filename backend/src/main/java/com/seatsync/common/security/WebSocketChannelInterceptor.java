package com.seatsync.common.security;

import com.seatsync.modules.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Channel interceptor securing inbound WebSocket STOMP frames:
 * 1. Authenticates CONNECT frames using JWT tokens.
 * 2. Authorizes SUBSCRIBE destinations to valid /topic/events/{eventId}/seats.
 * 3. Enforces read-only communication by rejecting incoming SEND frames.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final EventRepository eventRepository;

    private static final Pattern EVENT_SEATS_TOPIC_PATTERN = Pattern.compile("^/topic/events/(\\d+)/seats$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && accessor.getCommand() != null) {
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                handleConnect(accessor);
            } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                handleSubscribe(accessor);
            } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                handleSend(accessor);
            }
        }

        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            authHeader = accessor.getFirstNativeHeader("token");
        }

        if (authHeader == null || authHeader.isBlank()) {
            log.warn("WebSocket CONNECT rejected: Missing Authorization header");
            throw new BadCredentialsException("Missing Authorization header for WebSocket connection");
        }

        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7).trim() : authHeader.trim();

        if (!jwtTokenProvider.validateToken(token)) {
            log.warn("WebSocket CONNECT rejected: Invalid or expired token");
            throw new BadCredentialsException("Invalid or expired JWT token for WebSocket connection");
        }

        String email = jwtTokenProvider.getUsernameFromToken(token);
        org.springframework.security.core.userdetails.UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        if (accessor.isMutable()) {
            accessor.setUser(auth);
        }
        log.info("WebSocket connection authenticated for user '{}'", email);
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            throw new AccessDeniedException("Subscription destination cannot be null or empty");
        }

        Matcher matcher = EVENT_SEATS_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            log.warn("WebSocket SUBSCRIBE rejected: Invalid destination '{}'", destination);
            throw new AccessDeniedException("Unauthorized subscription destination: " + destination);
        }

        Long eventId = Long.parseLong(matcher.group(1));
        if (!eventRepository.existsById(eventId)) {
            log.warn("WebSocket SUBSCRIBE rejected: Event {} does not exist", eventId);
            throw new AccessDeniedException("Cannot subscribe to non-existent event: " + eventId);
        }

        log.debug("WebSocket subscription authorized for destination '{}'", destination);
    }

    private void handleSend(StompHeaderAccessor accessor) {
        // Enforce unidirectional updates: all state mutations must be performed via authenticated REST APIs
        log.warn("WebSocket SEND rejected: Inbound client messages to '{}' not permitted", accessor.getDestination());
        throw new AccessDeniedException("Client messages not permitted. Seat state transitions must be performed via secure REST APIs.");
    }
}
