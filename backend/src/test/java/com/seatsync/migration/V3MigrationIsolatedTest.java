package com.seatsync.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Isolated Test for V3 Migration (Phase 5.1).
 * 
 * Verifies V3 migration against a dedicated disposable MySQL database.
 * Confirms Flyway 10.21.0 compatibility, DELIMITER parsing, stored procedure execution & cleanup,
 * index transition, outbox table invariants, seat generation, pricing updates, state preservation,
 * and failure recovery via flyway:repair.
 * 
 * STRICT CONSTRAINT: Does NOT touch or modify seatsync_db.
 */
public class V3MigrationIsolatedTest {

    private static final String MYSQL_HOST = "localhost:3306";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "root";

    private static final String PRIMARY_TEST_DB = "seatsync_v3_isolated_test";
    private static final String RECOVERY_TEST_DB = "seatsync_v3_recovery_test";
    private static final String PROD_DB = "seatsync_db";

    private static final String ROOT_JDBC_URL = "jdbc:mysql://" + MYSQL_HOST + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String PRIMARY_JDBC_URL = "jdbc:mysql://" + MYSQL_HOST + "/" + PRIMARY_TEST_DB + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String RECOVERY_JDBC_URL = "jdbc:mysql://" + MYSQL_HOST + "/" + RECOVERY_TEST_DB + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String PROD_JDBC_URL = "jdbc:mysql://" + MYSQL_HOST + "/" + PROD_DB + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    private static int prodVenuesBaseline = -1;
    private static int prodEventsBaseline = -1;
    private static int prodFlywayBaseline = -1;

    @BeforeAll
    static void captureBaselineAndSetupDatabases() throws Exception {
        // 1. Capture baseline counts from existing seatsync_db
        try (Connection conn = DriverManager.getConnection(PROD_JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {
            
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM venues")) {
                if (rs.next()) prodVenuesBaseline = rs.getInt(1);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM events")) {
                if (rs.next()) prodEventsBaseline = rs.getInt(1);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM flyway_schema_history")) {
                if (rs.next()) prodFlywayBaseline = rs.getInt(1);
            }
        }

        // 2. Create clean disposable test databases
        try (Connection rootConn = DriverManager.getConnection(ROOT_JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = rootConn.createStatement()) {
            stmt.executeUpdate("DROP DATABASE IF EXISTS " + PRIMARY_TEST_DB);
            stmt.executeUpdate("CREATE DATABASE " + PRIMARY_TEST_DB + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");

            stmt.executeUpdate("DROP DATABASE IF EXISTS " + RECOVERY_TEST_DB);
            stmt.executeUpdate("CREATE DATABASE " + RECOVERY_TEST_DB + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    @Test
    @DisplayName("Task 1-10: Execute V3 on Disposable DB and Verify All Schema & Data Invariants")
    void testV3MigrationCompleteWorkflow() throws Exception {
        // Step 1: Migrate V1 and V2 using Flyway 10.21.0
        Flyway flywayV2 = Flyway.configure()
                .dataSource(PRIMARY_JDBC_URL, DB_USER, DB_PASS)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("2"))
                .load();

        MigrateResult resultV2 = flywayV2.migrate();
        assertTrue(resultV2.success, "Flyway V1+V2 migration must succeed");
        assertEquals("2", resultV2.targetSchemaVersion, "Target version must be 2");

        // Step 2: Seed intended venues, events, users, and pre-existing seats for verification
        try (Connection conn = DriverManager.getConnection(PRIMARY_JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {

            // Insert Organizer & Customer users
            stmt.executeUpdate("INSERT INTO users (id, email, password_hash, full_name, status) VALUES " +
                    "(101, 'org@test.ai', '$2a$10$xyz', 'Test Organizer', 'ACTIVE'), " +
                    "(102, 'cust@test.ai', '$2a$10$abc', 'Test Customer', 'ACTIVE') " +
                    "ON DUPLICATE KEY UPDATE id=id");

            // Insert 2 Intended Venues
            stmt.executeUpdate("INSERT INTO venues (id, name, slug, address, city, state, postal_code, capacity) VALUES " +
                    "(1, 'Mumbai Grand Arena 1', 'mumbai-arena-0378d1', 'BKC 1', 'Mumbai', 'Maharashtra', '400051', 25000), " +
                    "(2, 'Mumbai Grand Arena 2', 'mumbai-arena-24dc3d', 'BKC 2', 'Mumbai', 'Maharashtra', '400051', 25000) " +
                    "ON DUPLICATE KEY UPDATE id=id");

            // Insert 4 Intended Events (3 at Venue 1, 1 at Venue 2)
            stmt.executeUpdate("INSERT INTO events (id, organizer_id, category_id, venue_id, title, slug, short_description, start_time, end_time, base_price, status) VALUES " +
                    "(1, 101, 1, 1, 'Event 1 (Venue 1)', 'event-1', 'Short desc 1', '2026-12-01 10:00:00', '2026-12-01 14:00:00', 1999.00, 'PUBLISHED'), " +
                    "(2, 101, 1, 2, 'Event 2 (Venue 2)', 'event-2', 'Short desc 2', '2026-12-02 10:00:00', '2026-12-02 14:00:00', 1999.00, 'PUBLISHED'), " +
                    "(3, 101, 1, 1, 'Event 3 (Venue 1)', 'event-3', 'Short desc 3', '2026-12-03 10:00:00', '2026-12-03 14:00:00', 2999.00, 'PUBLISHED'), " +
                    "(4, 101, 1, 1, 'Event 4 (Venue 1)', 'event-4', 'Short desc 4', '2026-12-04 10:00:00', '2026-12-04 14:00:00', 2999.00, 'PUBLISHED') " +
                    "ON DUPLICATE KEY UPDATE id=id");

            // Pre-seed 1 Section for Venue 1 to test section existence and seat pre-population
            stmt.executeUpdate("INSERT INTO venue_sections (id, venue_id, name, section_code, tier_level, capacity, display_order) VALUES " +
                    "(10, 1, 'VIP Premium Club', 'VIP', 1, 40, 1) " +
                    "ON DUPLICATE KEY UPDATE id=id");

            // Pre-seed 3 Physical Seats in Venue 1 VIP Section
            stmt.executeUpdate("INSERT INTO seats (id, venue_section_id, row_label, seat_number, grid_x, grid_y) VALUES " +
                    "(1001, 10, 'A', 1, 1, 1), " +
                    "(1002, 10, 'A', 2, 2, 1), " +
                    "(1003, 10, 'A', 3, 3, 1) " +
                    "ON DUPLICATE KEY UPDATE id=id");

            // Insert a Confirmed Booking for Seat 1002
            stmt.executeUpdate("INSERT INTO bookings (id, booking_reference, user_id, event_id, status, total_amount, idempotency_key) VALUES " +
                    "(501, 'BK-CONFIRMED-001', 102, 1, 'CONFIRMED', 4500.00, 'idem-key-existing-001')");

            // Insert 3 Pre-existing Event Seats on Event 1 to test:
            // - Seat 1001: AVAILABLE with outdated price 500.00 (MUST be updated to 1999 * 2.50 = 4997.50)
            // - Seat 1002: BOOKED with price 4500.00 (MUST be preserved)
            // - Seat 1003: HELD with price 3500.00 (MUST be preserved)
            stmt.executeUpdate("INSERT INTO event_seats (event_id, seat_id, current_booking_id, status, price, held_by_user_id, held_until, hold_token, version) VALUES " +
                    "(1, 1001, NULL, 'AVAILABLE', 500.00, NULL, NULL, NULL, 0), " +
                    "(1, 1002, 501, 'BOOKED', 4500.00, NULL, NULL, NULL, 1), " +
                    "(1, 1003, NULL, 'HELD', 3500.00, 102, DATE_ADD(NOW(), INTERVAL 15 MINUTE), 'token-uuid-1234', 1)");
        }

        // Step 3: Run Flyway Migrate to Target 3 (V3)
        Flyway flywayV3 = Flyway.configure()
                .dataSource(PRIMARY_JDBC_URL, DB_USER, DB_PASS)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("3"))
                .load();

        MigrateResult resultV3 = flywayV3.migrate();
        assertTrue(resultV3.success, "Flyway V3 migration must succeed");
        assertEquals("3", resultV3.targetSchemaVersion, "Target version must be 3");

        // Step 4: Verify Database Invariants via JDBC
        try (Connection conn = DriverManager.getConnection(PRIMARY_JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {

            // 4.1 Stored procedure cleanup: 0 procedures remaining
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema = DATABASE()")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "All temporary stored procedures must be dropped after migration");
            }

            // 4.2 Composite idempotency index uk_user_idempotency exists with exact 2 columns
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*), " +
                    "SUM(CASE WHEN seq_in_index = 1 AND column_name = 'user_id' THEN 1 ELSE 0 END), " +
                    "SUM(CASE WHEN seq_in_index = 2 AND column_name = 'idempotency_key' THEN 1 ELSE 0 END) " +
                    "FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'bookings' AND index_name = 'uk_user_idempotency'")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "uk_user_idempotency must have exactly 2 columns");
                assertEquals(1, rs.getInt(2), "Column 1 must be user_id");
                assertEquals(1, rs.getInt(3), "Column 2 must be idempotency_key");
            }

            // 4.3 Legacy single-column unique index on idempotency_key is dropped
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'bookings' " +
                    "AND column_name = 'idempotency_key' AND non_unique = 0 AND index_name != 'uk_user_idempotency'")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Legacy single-column unique index on idempotency_key must be removed");
            }

            // 4.4 Outbox table validation: exactly 14 columns
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'outbox_events'")) {
                assertTrue(rs.next());
                assertEquals(14, rs.getInt(1), "outbox_events must have exactly 14 columns");
            }

            // 4.5 Outbox indexes validation: exactly 4 canonical indexes, 8 index rows, 0 prefix indexes
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(DISTINCT index_name), COUNT(*), " +
                    "SUM(CASE WHEN sub_part IS NOT NULL THEN 1 ELSE 0 END) " +
                    "FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'outbox_events'")) {
                assertTrue(rs.next());
                assertEquals(4, rs.getInt(1), "outbox_events must have exactly 4 indexes");
                assertEquals(8, rs.getInt(2), "outbox_events must have 8 total index rows");
                assertEquals(0, rs.getInt(3), "outbox_events must have 0 prefix indexes");
            }

            // 4.6 Verify 6 venue sections (3 per venue)
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM venue_sections")) {
                assertTrue(rs.next());
                assertEquals(6, rs.getInt(1), "Must have exactly 6 venue sections across 2 venues");
            }

            // 4.7 Verify 350 physical seats (175 per venue)
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM seats")) {
                assertTrue(rs.next());
                assertEquals(350, rs.getInt(1), "Must have exactly 350 physical seats across 2 venues");
            }

            // 4.8 Verify 700 event seats (175 per event * 4 events)
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM event_seats")) {
                assertTrue(rs.next());
                assertEquals(700, rs.getInt(1), "Must have exactly 700 event seats across 4 events");
            }

            // 4.9 Verify ZERO cross-venue seats (no event receives seats from another venue)
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM event_seats es " +
                    "JOIN events e ON es.event_id = e.id " +
                    "JOIN seats s ON es.seat_id = s.id " +
                    "JOIN venue_sections vs ON s.venue_section_id = vs.id " +
                    "WHERE e.venue_id != vs.venue_id")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Zero event seats should have cross-venue mismatches");
            }

            // 4.10 Verify existing seat price updates and state preservation
            // Seat 1001 (AVAILABLE): price was 500.00, must be updated to 1999 * 2.50 = 4997.50
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT status, price FROM event_seats WHERE event_id = 1 AND seat_id = 1001")) {
                assertTrue(rs.next());
                assertEquals("AVAILABLE", rs.getString("status"));
                assertEquals(new BigDecimal("4997.50"), rs.getBigDecimal("price"),
                        "AVAILABLE seat price must be synchronized to tier formula");
            }

            // Seat 1002 (BOOKED): price was 4500.00, booking_id = 501. MUST BE PRESERVED!
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT status, price, current_booking_id FROM event_seats WHERE event_id = 1 AND seat_id = 1002")) {
                assertTrue(rs.next());
                assertEquals("BOOKED", rs.getString("status"));
                assertEquals(new BigDecimal("4500.00"), rs.getBigDecimal("price"),
                        "BOOKED seat price must be preserved");
                assertEquals(501, rs.getLong("current_booking_id"),
                        "BOOKED seat current_booking_id must be preserved");
            }

            // Seat 1003 (HELD): price was 3500.00, held_by = 102, hold_token = 'token-uuid-1234'. MUST BE PRESERVED!
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT status, price, held_by_user_id, hold_token FROM event_seats WHERE event_id = 1 AND seat_id = 1003")) {
                assertTrue(rs.next());
                assertEquals("HELD", rs.getString("status"));
                assertEquals(new BigDecimal("3500.00"), rs.getBigDecimal("price"),
                        "HELD seat price must be preserved");
                assertEquals(102, rs.getLong("held_by_user_id"),
                        "HELD seat held_by_user_id must be preserved");
                assertEquals("token-uuid-1234", rs.getString("hold_token"),
                        "HELD seat hold_token must be preserved");
            }
        }
    }

    @Test
    @DisplayName("Task 11-12: Test Failure Injection, Flyway Repair, and Safe Re-Run on Recovery DB")
    void testFailureInjectionRepairAndRerun() throws Exception {
        // Step 1: Migrate V1 and V2 on RECOVERY_TEST_DB
        Flyway flywayRec = Flyway.configure()
                .dataSource(RECOVERY_JDBC_URL, DB_USER, DB_PASS)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("2"))
                .load();

        flywayRec.migrate();

        // Step 2: Seed venues, events, and INJECT A FAILURE (non-positive base price)
        try (Connection conn = DriverManager.getConnection(RECOVERY_JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {

            // Insert Users
            stmt.executeUpdate("INSERT INTO users (id, email, password_hash, full_name, status) VALUES " +
                    "(201, 'org2@test.ai', '$2a$10$xyz', 'Org 2', 'ACTIVE'), " +
                    "(202, 'cust2@test.ai', '$2a$10$abc', 'Cust 2', 'ACTIVE')");

            // Insert Venues
            stmt.executeUpdate("INSERT INTO venues (id, name, slug, address, city, state, postal_code, capacity) VALUES " +
                    "(1, 'Venue 1', 'mumbai-arena-0378d1', 'BKC', 'Mumbai', 'MH', '400051', 25000), " +
                    "(2, 'Venue 2', 'mumbai-arena-24dc3d', 'BKC', 'Mumbai', 'MH', '400051', 25000)");

            // Insert Events with one invalid event price
            stmt.executeUpdate("INSERT INTO events (id, organizer_id, category_id, venue_id, title, slug, short_description, start_time, end_time, base_price, status) VALUES " +
                    "(1, 201, 1, 1, 'Event 1', 'ev-1', 'Desc 1', '2026-12-01 10:00:00', '2026-12-01 14:00:00', 1999.00, 'PUBLISHED'), " +
                    "(2, 201, 1, 2, 'Event 2', 'ev-2', 'Desc 2', '2026-12-02 10:00:00', '2026-12-02 14:00:00', 1999.00, 'PUBLISHED'), " +
                    "(3, 201, 1, 1, 'Event 3', 'ev-3', 'Desc 3', '2026-12-03 10:00:00', '2026-12-03 14:00:00', 2999.00, 'PUBLISHED'), " +
                    "(4, 201, 1, 1, 'Event 4', 'ev-4', 'Desc 4', '2026-12-04 10:00:00', '2026-12-04 14:00:00', -100.00, 'PUBLISHED')");
        }

        // Step 3: Run Flyway Migrate to V3 -> Expect Preflight Failure!
        Flyway flywayV3Fail = Flyway.configure()
                .dataSource(RECOVERY_JDBC_URL, DB_USER, DB_PASS)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("3"))
                .load();

        Exception migrationException = null;
        try {
            flywayV3Fail.migrate();
        } catch (Exception ex) {
            migrationException = ex;
        }

        assertNotNull(migrationException, "Migration must fail due to negative base_price preflight check");
        String fullError = migrationException.toString();
        Throwable cause = migrationException.getCause();
        while (cause != null) {
            fullError += " | " + cause.toString();
            cause = cause.getCause();
        }
        assertTrue(fullError.contains("ERR_EVENT_BASE_PRICE"),
                "Exception message must contain ERR_EVENT_BASE_PRICE. Full error: " + fullError);

        // Step 4: Verify that flyway_schema_history records failure
        try (Connection conn = DriverManager.getConnection(RECOVERY_JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT success FROM flyway_schema_history WHERE version = '3'")) {
            assertTrue(rs.next(), "V3 row must exist in schema history");
            assertFalse(rs.getBoolean("success"), "V3 status must be failed (success = 0)");
        }

        // Step 5: Execute flyway.repair() to clear failed state
        flywayV3Fail.repair();

        // Step 6: Fix the data condition (restore positive base_price)
        try (Connection conn = DriverManager.getConnection(RECOVERY_JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE events SET base_price = 2999.00 WHERE id = 4");
        }

        // Step 7: Re-run Flyway Migrate to V3 -> Must SUCCEED completely!
        MigrateResult retryResult = flywayV3Fail.migrate();
        assertTrue(retryResult.success, "Migration retry after repair must succeed");
        assertEquals("3", retryResult.targetSchemaVersion);

        // Step 8: Verify all 700 event seats exist in recovery DB
        try (Connection conn = DriverManager.getConnection(RECOVERY_JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) FROM event_seats")) {
            assertTrue(rs.next());
            assertEquals(700, rs.getInt(1), "Recovery DB must have 700 event seats after clean re-run");
        }
    }

    @AfterAll
    static void verifyProdDbUntouchedAndCleanUp() throws Exception {
        // Assert that seatsync_db was NEVER modified
        try (Connection conn = DriverManager.getConnection(PROD_JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM venues")) {
                assertTrue(rs.next());
                assertEquals(prodVenuesBaseline, rs.getInt(1), "Original seatsync_db venues count must be untouched");
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM events")) {
                assertTrue(rs.next());
                assertEquals(prodEventsBaseline, rs.getInt(1), "Original seatsync_db events count must be untouched");
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM flyway_schema_history")) {
                assertTrue(rs.next());
                assertEquals(prodFlywayBaseline, rs.getInt(1), "Original seatsync_db flyway_schema_history must be untouched");
            }
        }
    }
}
