package com.seatsync.event;

import com.seatsync.modules.event.entity.Venue;
import com.seatsync.modules.event.repository.VenueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class VenueRepositoryTest {

    @Autowired
    private VenueRepository venueRepository;

    @Test
    @DisplayName("Should persist venue and filter by city")
    void shouldPersistAndFilterVenuesByCity() {
        Venue venue1 = Venue.builder()
                .name("Gachibowli Stadium")
                .slug("gachibowli-stadium-hyderabad")
                .address("Old Mumbai Highway, Gachibowli")
                .city("Hyderabad")
                .state("Telangana")
                .postalCode("500032")
                .country("India")
                .capacity(25000)
                .latitude(new BigDecimal("17.4474"))
                .longitude(new BigDecimal("78.3489"))
                .build();

        Venue venue2 = Venue.builder()
                .name("Jawaharlal Nehru Stadium")
                .slug("jawaharlal-nehru-stadium-delhi")
                .address("Pragati Vihar")
                .city("New Delhi")
                .state("Delhi")
                .postalCode("110003")
                .country("India")
                .capacity(60000)
                .build();

        venueRepository.save(venue1);
        venueRepository.save(venue2);

        Page<Venue> hyderabadVenues = venueRepository.findByCityIgnoreCase("hyderabad", PageRequest.of(0, 10));
        assertEquals(1, hyderabadVenues.getTotalElements());
        assertEquals("Gachibowli Stadium", hyderabadVenues.getContent().get(0).getName());

        assertTrue(venueRepository.existsBySlug("gachibowli-stadium-hyderabad"));
        assertFalse(venueRepository.existsBySlug("non-existent-venue"));
    }
}

