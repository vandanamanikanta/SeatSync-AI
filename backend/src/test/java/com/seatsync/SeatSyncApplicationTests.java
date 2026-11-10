package com.seatsync;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SeatSyncApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the complete Spring ApplicationContext, Security filter chain,
        // and JPA configurations load cleanly without bean wiring or context errors.
    }
}

