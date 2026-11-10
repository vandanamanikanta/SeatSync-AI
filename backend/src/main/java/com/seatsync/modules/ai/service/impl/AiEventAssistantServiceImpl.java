package com.seatsync.modules.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatsync.modules.ai.config.GeminiConfig;
import com.seatsync.modules.ai.dto.AiChatRequest;
import com.seatsync.modules.ai.dto.AiChatResponse;
import com.seatsync.modules.ai.dto.AiChatResponse.AiEventCardDto;
import com.seatsync.modules.ai.dto.ExtractedEventFilters;
import com.seatsync.modules.ai.service.AiEventAssistantService;
import com.seatsync.modules.event.dto.EventSearchCriteria;
import com.seatsync.modules.event.entity.Event;
import com.seatsync.modules.event.entity.EventStatus;
import com.seatsync.modules.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiEventAssistantServiceImpl implements AiEventAssistantService {

    private final ChatModel chatModel;
    private final GeminiConfig geminiConfig;
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public AiChatResponse processChat(AiChatRequest request, Long currentUserId) {
        String userMessage = request.getMessage().trim();
        log.info("Processing AI Chat query for user [{}]: '{}'", currentUserId, userMessage);

        // Step 1: Extract structured filters via Gemini NLP
        ExtractedEventFilters filters = extractFilters(userMessage);
        log.debug("Extracted filters: {}", filters);

        // Step 2: Query authoritative MySQL database using EventSearchCriteria
        EventSearchCriteria criteria = buildCriteriaFromFilters(filters);
        Page<Event> eventPage = eventRepository.findAll(
                criteria.toSpecification(),
                PageRequest.of(0, 8, Sort.by("startTime").ascending())
        );
        List<Event> matchingEvents = eventPage.getContent();
        log.info("Database returned {} matching events for criteria", matchingEvents.size());

        // Step 3: Grounded RAG response synthesis via Gemini
        String aiResponseText = generateGroundedResponse(userMessage, filters, matchingEvents);

        // Step 4: Map entities to lightweight card DTOs for the React UI
        List<AiEventCardDto> cardDtos = matchingEvents.stream()
                .map(this::mapToEventCardDto)
                .toList();

        return AiChatResponse.builder()
                .response(aiResponseText)
                .intent(filters.getIntent())
                .extractedFilters(filters)
                .events(cardDtos)
                .totalMatches((int) eventPage.getTotalElements())
                .grounded(true)
                .liveGeminiUsed(geminiConfig.isLiveGeminiConfigured())
                .provider(geminiConfig.isLiveGeminiConfigured() ? "Google Gemini (" + geminiConfig.getResolvedModelName() + ")" : "SeatSync Fallback Concierge")
                .build();
    }

    @Override
    public ExtractedEventFilters extractFilters(String userMessage) {
        String systemPrompt = """
                You are an NLP entity extractor for SeatSync AI event discovery platform.
                Extract structured search parameters from the user's query into a JSON object with these exact keys:
                - "intent": one of ["SEARCH_EVENTS", "RECOMMEND_SIMILAR", "PRICE_QUERY", "DATE_QUERY", "GENERAL_QUERY"]
                - "category": category slug if referenced (one of "concerts-music", "standup-comedy", "sports-fitness", "theatre-arts", "tech-conferences", "edm-festivals-2b8e2e") or null
                - "city": city name if mentioned (e.g. "Mumbai", "Bengaluru", "Hyderabad", "Delhi", "Pune") or null
                - "keyword": search keyword/artist/title or null
                - "maxBudget": maximum price in INR as numeric value (e.g. 2000) or null
                - "minBudget": minimum price in INR or null
                - "timeframe": one of ["TODAY", "TOMORROW", "THIS_WEEKEND", "UPCOMING"] or null

                User Query: "%s"

                Return ONLY a valid JSON object without markdown or code blocks.
                """.formatted(userMessage);

        try {
            String rawResponse = chatModel.call(new Prompt(systemPrompt)).getResult().getOutput().getText();
            String cleanJson = sanitizeJson(rawResponse);
            JsonNode node = objectMapper.readTree(cleanJson);

            String intent = node.hasNonNull("intent") ? node.get("intent").asText() : "SEARCH_EVENTS";
            String category = node.hasNonNull("category") ? node.get("category").asText() : null;
            String city = node.hasNonNull("city") ? node.get("city").asText() : null;
            String keyword = node.hasNonNull("keyword") ? node.get("keyword").asText() : null;
            String timeframe = node.hasNonNull("timeframe") ? node.get("timeframe").asText() : null;

            BigDecimal maxBudget = null;
            if (node.hasNonNull("maxBudget")) {
                maxBudget = new BigDecimal(node.get("maxBudget").asText());
            }
            BigDecimal minBudget = null;
            if (node.hasNonNull("minBudget")) {
                minBudget = new BigDecimal(node.get("minBudget").asText());
            }

            return ExtractedEventFilters.builder()
                    .intent(intent)
                    .category(category)
                    .city(city)
                    .keyword(keyword)
                    .maxBudget(maxBudget)
                    .minBudget(minBudget)
                    .timeframe(timeframe)
                    .build();
        } catch (Exception e) {
            log.warn("Gemini NLP extraction threw exception: {}. Falling back to heuristic parsing.", e.getMessage());
            return fallbackHeuristicExtraction(userMessage);
        }
    }

    @Override
    public boolean isLiveGeminiActive() {
        return geminiConfig.isLiveGeminiConfigured();
    }

    private EventSearchCriteria buildCriteriaFromFilters(ExtractedEventFilters filters) {
        EventSearchCriteria.EventSearchCriteriaBuilder builder = EventSearchCriteria.builder()
                .status(EventStatus.PUBLISHED);

        if (StringUtils.hasText(filters.getCity())) {
            builder.city(filters.getCity());
        }
        if (StringUtils.hasText(filters.getCategory())) {
            builder.category(filters.getCategory());
        }
        if (StringUtils.hasText(filters.getKeyword())) {
            builder.search(filters.getKeyword());
        }
        if (filters.getMaxBudget() != null) {
            builder.maxPrice(filters.getMaxBudget());
        }
        if (filters.getMinBudget() != null) {
            builder.minPrice(filters.getMinBudget());
        }

        // Date range calculation
        if (StringUtils.hasText(filters.getTimeframe())) {
            ZoneId zone = ZoneId.of("Asia/Kolkata");
            LocalDate today = LocalDate.now(zone);

            switch (filters.getTimeframe().toUpperCase(Locale.ROOT)) {
                case "TODAY" -> {
                    builder.startDate(today.atStartOfDay(zone).toInstant());
                    builder.endDate(today.plusDays(1).atStartOfDay(zone).minusNanos(1).toInstant());
                }
                case "TOMORROW" -> {
                    LocalDate tomorrow = today.plusDays(1);
                    builder.startDate(tomorrow.atStartOfDay(zone).toInstant());
                    builder.endDate(tomorrow.plusDays(1).atStartOfDay(zone).minusNanos(1).toInstant());
                }
                case "THIS_WEEKEND" -> {
                    LocalDate friday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
                    LocalDate sunday = friday.plusDays(2);
                    builder.startDate(friday.atStartOfDay(zone).toInstant());
                    builder.endDate(sunday.plusDays(1).atStartOfDay(zone).minusNanos(1).toInstant());
                }
                case "UPCOMING" -> {
                    builder.startDate(Instant.now());
                }
                default -> {}
            }
        }

        return builder.build();
    }

    private String generateGroundedResponse(String userMessage, ExtractedEventFilters filters, List<Event> matchingEvents) {
        StringBuilder catalogContext = new StringBuilder();
        if (matchingEvents.isEmpty()) {
            catalogContext.append("CATALOG IS EMPTY: No events matched the search criteria (City: ")
                    .append(filters.getCity() != null ? filters.getCity() : "Any")
                    .append(", Category: ")
                    .append(filters.getCategory() != null ? filters.getCategory() : "Any")
                    .append(", Max Budget: ")
                    .append(filters.getMaxBudget() != null ? "₹" + filters.getMaxBudget() : "Any")
                    .append(").");
        } else {
            catalogContext.append("AVAILABLE EVENT CATALOG IN SEATSYNC AI:\n");
            for (Event e : matchingEvents) {
                catalogContext.append(String.format(
                        "- Event ID: %d | Title: \"%s\" | Category: %s | City: %s | Venue: %s | Start Date: %s | Base Price: ₹%s\n  Description: %s\n",
                        e.getId(),
                        e.getTitle(),
                        e.getCategory() != null ? e.getCategory().getName() : "General",
                        e.getVenue() != null ? e.getVenue().getCity() : "N/A",
                        e.getVenue() != null ? e.getVenue().getName() : "N/A",
                        e.getStartTime(),
                        e.getBasePrice(),
                        e.getShortDescription()
                ));
            }
        }

        String ragPrompt = """
                You are SeatSync AI's intelligent event concierge.
                Answer the user's question accurately using ONLY the provided catalog context below.

                CRITICAL GROUNDING RULES:
                1. You must ONLY reference real events provided in the CATALOG below.
                2. NEVER hallucinate, invent, or assume any event name, artist, venue, price, date, or availability.
                3. If matching events exist in the catalog, highlight them with their exact name, city, venue, and starting price. Mention that users can select live seats and book directly.
                4. If NO events match (catalog is empty), state this clearly and politely. If relevant, suggest exploring upcoming concerts in Mumbai or broadening search criteria.
                5. Keep your tone enthusiastic, polite, and concise (2-4 sentences).

                USER QUERY: "%s"

                %s
                """.formatted(userMessage, catalogContext.toString());

        try {
            return chatModel.call(new Prompt(ragPrompt)).getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("Gemini grounded generation failed: {}. Providing fallback grounded summary.", e.getMessage());
            if (matchingEvents.isEmpty()) {
                return "I searched our catalog, but there are currently no events matching your requested criteria. " +
                       "You can explore other live music events in Mumbai on SeatSync AI!";
            } else {
                Event first = matchingEvents.get(0);
                return String.format(
                        "I found %d event(s) matching your search! Check out '%s' at %s (%s) starting at ₹%s. " +
                        "Select your seats across VIP, Platinum, or Gold tiers to book directly on SeatSync AI.",
                        matchingEvents.size(),
                        first.getTitle(),
                        first.getVenue() != null ? first.getVenue().getName() : "the venue",
                        first.getVenue() != null ? first.getVenue().getCity() : "Mumbai",
                        first.getBasePrice()
                );
            }
        }
    }

    private AiEventCardDto mapToEventCardDto(Event e) {
        return AiEventCardDto.builder()
                .id(e.getId())
                .title(e.getTitle())
                .slug(e.getSlug())
                .shortDescription(e.getShortDescription())
                .bannerImageUrl(e.getBannerImageUrl())
                .startTime(e.getStartTime())
                .basePrice(e.getBasePrice())
                .categoryName(e.getCategory() != null ? e.getCategory().getName() : null)
                .categorySlug(e.getCategory() != null ? e.getCategory().getSlug() : null)
                .venueName(e.getVenue() != null ? e.getVenue().getName() : null)
                .city(e.getVenue() != null ? e.getVenue().getCity() : null)
                .build();
    }

    private String sanitizeJson(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        if (s.startsWith("```json")) {
            s = s.substring(7);
        } else if (s.startsWith("```")) {
            s = s.substring(3);
        }
        if (s.endsWith("```")) {
            s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }

    private ExtractedEventFilters fallbackHeuristicExtraction(String msg) {
        String lower = msg.toLowerCase(Locale.ROOT);
        String category = null;
        if (lower.contains("music") || lower.contains("concert") || lower.contains("rock")) {
            category = "concerts-music";
        } else if (lower.contains("comedy") || lower.contains("standup")) {
            category = "standup-comedy";
        }

        String city = null;
        if (lower.contains("mumbai")) city = "Mumbai";
        else if (lower.contains("bengaluru") || lower.contains("bangalore")) city = "Bengaluru";
        else if (lower.contains("hyderabad")) city = "Hyderabad";
        else if (lower.contains("delhi")) city = "Delhi";

        BigDecimal maxBudget = null;
        if (lower.contains("2000") || lower.contains("2,000")) {
            maxBudget = new BigDecimal("2000");
        } else if (lower.contains("1000") || lower.contains("1,000")) {
            maxBudget = new BigDecimal("1000");
        }

        String timeframe = null;
        if (lower.contains("weekend")) timeframe = "THIS_WEEKEND";
        else if (lower.contains("today")) timeframe = "TODAY";
        else if (lower.contains("tomorrow")) timeframe = "TOMORROW";

        return ExtractedEventFilters.builder()
                .intent("SEARCH_EVENTS")
                .category(category)
                .city(city)
                .maxBudget(maxBudget)
                .timeframe(timeframe)
                .build();
    }
}

