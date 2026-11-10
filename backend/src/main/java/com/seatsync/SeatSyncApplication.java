package com.seatsync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SeatSync AI - Intelligent Event Discovery, Recommendation & Real-Time Booking Platform
 * Production-grade modular monolith backend built on Spring Boot 3.4.2 and Java 21 LTS.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SeatSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeatSyncApplication.class, args);
    }
}

