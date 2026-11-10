package com.seatsync.common.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.Socket;

/**
 * Redis configuration providing connection templates, atomic Lua scripts,
 * and an auto-starting embedded Redis server for seamless local development and tests
 * when external Redis is not running.
 */
@Slf4j
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.embedded.enabled:true}")
    private boolean embeddedRedisEnabled;

    private RedisServer embeddedRedisServer;

    @PostConstruct
    public void startEmbeddedRedisIfNecessary() {
        if (!embeddedRedisEnabled) {
            log.info("Embedded Redis is explicitly disabled via configuration");
            return;
        }

        if (isPortInUse(redisHost, redisPort)) {
            log.info("Redis server is already listening on {}:{} - using existing instance", redisHost, redisPort);
            return;
        }

        try {
            log.info("No external Redis detected on {}:{}. Starting embedded Redis Server...", redisHost, redisPort);
            embeddedRedisServer = new RedisServer(redisPort);
            embeddedRedisServer.start();
            log.info("Embedded Redis Server successfully started on port {}", redisPort);
        } catch (Exception e) {
            log.warn("Could not start embedded Redis on port {}: {}. Will attempt direct connection.", redisPort, e.getMessage());
        }
    }

    @PreDestroy
    public void stopEmbeddedRedis() {
        if (embeddedRedisServer != null && embeddedRedisServer.isActive()) {
            try {
                embeddedRedisServer.stop();
                log.info("Embedded Redis Server stopped cleanly");
            } catch (Exception e) {
                log.warn("Error while stopping embedded Redis: {}", e.getMessage());
            }
        }
    }

    private boolean isPortInUse(String host, int port) {
        try (Socket socket = new Socket(host, port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Atomic Multi-Seat Hold Acquisition Lua Script.
     * All-or-nothing: if ANY key in KEYS already exists, returns 0 and leaves all keys unmodified.
     * If all keys are available, sets all keys to holdToken with EX = ttlSeconds and returns 1.
     */
    @Bean
    public RedisScript<Long> multiSeatHoldScript() {
        String script =
                "for i, key in ipairs(KEYS) do\n" +
                "    if redis.call('EXISTS', key) == 1 then\n" +
                "        return 0\n" +
                "    end\n" +
                "end\n" +
                "for i, key in ipairs(KEYS) do\n" +
                "    redis.call('SET', key, ARGV[1], 'EX', ARGV[2])\n" +
                "end\n" +
                "return 1\n";
        return new DefaultRedisScript<>(script, Long.class);
    }

    /**
     * Atomic Multi-Seat Compare-and-Delete (CAD) Lua Script.
     * Strictly verifies that the current key value in Redis matches the expectedHoldToken (ARGV[1])
     * before deleting. Never executes blind deletions.
     */
    @Bean
    public RedisScript<Long> multiSeatCadScript() {
        String script =
                "local released = 0\n" +
                "for i, key in ipairs(KEYS) do\n" +
                "    if redis.call('GET', key) == ARGV[1] then\n" +
                "        redis.call('DEL', key)\n" +
                "        released = released + 1\n" +
                "    end\n" +
                "end\n" +
                "return released\n";
        return new DefaultRedisScript<>(script, Long.class);
    }

    /**
     * Single-Seat Compare-and-Delete (CAD) Lua Script.
     * Returns 1 if key existed and matched ARGV[1], 0 otherwise.
     */
    @Bean
    public RedisScript<Long> singleSeatCadScript() {
        String script =
                "if redis.call('GET', KEYS[1]) == ARGV[1] then\n" +
                "    return redis.call('DEL', KEYS[1])\n" +
                "else\n" +
                "    return 0\n" +
                "end\n";
        return new DefaultRedisScript<>(script, Long.class);
    }
}

