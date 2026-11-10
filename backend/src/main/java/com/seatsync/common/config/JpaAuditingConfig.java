package com.seatsync.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA Auditing for automatic timestamp management.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}

