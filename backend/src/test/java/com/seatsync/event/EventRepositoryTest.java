package com.seatsync.event;

import com.seatsync.modules.event.dto.EventSearchCriteria;
import com.seatsync.modules.event.entity.Event;
import com.seatsync.modules.event.entity.EventCategory;
import com.seatsync.modules.event.entity.EventStatus;
import com.seatsync.modules.event.entity.Venue;
import com.seatsync.modules.event.repository.EventCategoryRepository;
import com.seatsync.modules.event.repository.EventRepository;
import com.seatsync.modules.event.repository.VenueRepository;
import com.seatsync.modules.user.entity.User;
import com.seatsync.modules.user.entity.UserStatus;
import com.seatsync.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class EventRepositoryTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventCategoryRepository categoryRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private UserRepository userRepository;

    private User organizer;
    private EventCategory concertCategory;
    private EventCategory sportsCategory;
    private Venue hyderabadVenue;
    private Venue bangaloreVenue;

    @BeforeEach
    void setUp() {
        organizer = userRepository.save(User.builder()
                .email("org.event@seatsync.ai")
                .passwordHash("hashed")
                .fullName("Organizer Event")
                .status(UserStatus.ACTIVE)
                .build());

        concertCategory = categoryRepository.save(EventCategory.builder()
                .name("Concerts & Music")
                .slug("concerts-music")
                .build());

        sportsCategory = categoryRepository.save(EventCategory.builder()
                .name("Sports & Arena")
                .slug("sports-arena")
                .build());

        hyderabadVenue = venueRepository.save(Venue.builder()
                .name("Gachibowli Stadium")
                .slug("gachibowli-stadium")
                .address("Gachibowli")
                .city("Hyderabad")
                .state("Telangana")
                .postalCode("500032")
                .capacity(20000)
                .build());

        bangaloreVenue = venueRepository.save(Venue.builder()
                .name("Chinnaswamy Stadium")
                .slug("chinnaswamy-stadium")
                .address("MG Road")
                .city("Bangalore")
                .state("Karnataka")
                .postalCode("560001")
                .capacity(35000)
                .build());
    }

    @Test
    @DisplayName("Should filter events dynamically by search query, city, category, and price")
    void shouldFilterEventsWithSpecification() {
        Instant now = Instant.now();

        Event event1 = Event.builder()
                .organizer(organizer)
                .category(concertCategory)
                .venue(hyderabadVenue)
                .title("Acoustic Sunset Live Tour")
                .slug("acoustic-sunset-live-tour")
                .shortDescription("Peaceful acoustic musical evening")
                .startTime(now.plus(2, ChronoUnit.DAYS))
                .endTime(now.plus(2, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
                .basePrice(new BigDecimal("999.00"))
                .status(EventStatus.PUBLISHED)
                .build();

        Event event2 = Event.builder()
                .organizer(organizer)
                .category(sportsCategory)
                .venue(bangaloreVenue)
                .title("Cricket Championship Derby")
                .slug("cricket-championship-derby")
                .shortDescription("High adrenaline T20 action")
                .startTime(now.plus(5, ChronoUnit.DAYS))
                .endTime(now.plus(5, ChronoUnit.DAYS).plus(4, ChronoUnit.HOURS))
                .basePrice(new BigDecimal("2499.00"))
                .status(EventStatus.PUBLISHED)
                .build();

        Event event3 = Event.builder()
                .organizer(organizer)
                .category(concertCategory)
                .venue(hyderabadVenue)
                .title("Electronic Dance Rave")
                .slug("electronic-dance-rave")
                .shortDescription("Bass heavy EDM night")
                .startTime(now.plus(10, ChronoUnit.DAYS))
                .endTime(now.plus(10, ChronoUnit.DAYS).plus(5, ChronoUnit.HOURS))
                .basePrice(new BigDecimal("1500.00"))
                .status(EventStatus.DRAFT)
                .build();

        eventRepository.save(event1);
        eventRepository.save(event2);
        eventRepository.save(event3);

        // Search by keyword "Acoustic"
        EventSearchCriteria criteria1 = EventSearchCriteria.builder().search("acoustic").build();
        Page<Event> res1 = eventRepository.findAll(criteria1.toSpecification(), PageRequest.of(0, 10));
        assertEquals(1, res1.getTotalElements());
        assertEquals("Acoustic Sunset Live Tour", res1.getContent().get(0).getTitle());

        // Search by City "Hyderabad" and status PUBLISHED
        EventSearchCriteria criteria2 = EventSearchCriteria.builder()
                .city("Hyderabad")
                .status(EventStatus.PUBLISHED)
                .build();
        Page<Event> res2 = eventRepository.findAll(criteria2.toSpecification(), PageRequest.of(0, 10));
        assertEquals(1, res2.getTotalElements());
        assertEquals("Acoustic Sunset Live Tour", res2.getContent().get(0).getTitle());

        // Search by price range max ₹1200
        EventSearchCriteria criteria3 = EventSearchCriteria.builder()
                .maxPrice(new BigDecimal("1200.00"))
                .status(EventStatus.PUBLISHED)
                .build();
        Page<Event> res3 = eventRepository.findAll(criteria3.toSpecification(), PageRequest.of(0, 10));
        assertEquals(1, res3.getTotalElements());
        assertEquals("Acoustic Sunset Live Tour", res3.getContent().get(0).getTitle());
    }
}

