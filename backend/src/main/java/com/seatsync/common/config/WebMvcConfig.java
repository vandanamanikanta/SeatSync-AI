package com.seatsync.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Web MVC custom configurations.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    // Extensible for custom argument resolvers or formatters
}

