package com.seatsync.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Partial-DDL Failure Recovery Test for V3 Migration (Phase 5.1).
 * 
 * Verifies recovery from MySQL partial-DDL commits:
 * - Scenario A: Failure AFTER composite index (uk_user_idempotency) creation, before outbox completion.
 * - Scenario B: Failure AFTER outbox_events table creation, before physical seat seeding completes.
 * 
 * Demonstrates:
 * 1. Database state upon mid-migration failure (committed objects vs unseeded tables).
 * 2. Unbroken enforcement of constraints created before failure.
 * 3. Execution of flyway.repair() to clear failed schema history status.
 * 4. Error correction in the environment without modifying V3 migration code.
 * 5. Safe, idempotent re-run of V3 completing with success = 1.
 * 
 * STRICT CONSTRAINT: Original seatsync_db is NEVER modified.
 */
public class V3PartialDdlRecoveryTest {

    private static final String MYSQL_HOST = "localhost:3306";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "root";

    private static final String SCENARIO_A_DB = "seatsync_v3_partial_idx_test";
    private static final String SCENARIO_B_DB = "seatsync_v3_partial_outbox_test";
    private static final String PROD_DB = "seatsync_db";

    private static final String ROOT_JDBC_URL = "jdbc:mysql://" + MYSQL_HOST + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String SCENARIO_A_JDBC_URL = "jdbc:mysql://" + MYSQL_HOST + "/" + SCENARIO_A_DB + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String SCENARIO_B_JDBC_URL = "jdbc:mysql://" + MYSQL_HOST + "/" + SCENARIO_B_DB + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
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

        // 2. Create clean disposable databases for both scenarios
        try (Connection rootConn = DriverManager.getConnection(ROOT_JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = rootConn.createStatement()) {
            stmt.executeUpdate("DROP DATABASE IF EXISTS " + SCENARIO_A_DB);
            stmt.executeUpdate("CREATE DATABASE " + SCENARIO_A_DB + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");

            stmt.executeUpdate("DROP DATABASE IF EXISTS " + SCENARIO_B_DB);
            stmt.executeUpdate("CREATE DATABASE " + SCENARIO_B_DB + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    @Test
    @DisplayName("Scenario A: Failure AFTER Composite Index Creation, Before Outbox Completion")
    void testFailureAfterCompositeIndexBeforeOutbox() throws Exception {
        // Step 1: Migrate V1 and V2 on SCENARIO_A_DB
        Flyway flywayA = Flyway.configure()
                .dataSource(SCENARIO_A_JDBC_URL, DB_USER, DB_PASS)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("2"))
                .load();

        flywayA.migrate();

        // Step 2: Seed venues, events, users, and INJECT INCOMPATIBLE outbox_events TABLE
        // This causes Step 3 (index replacement) to SUCCEED, and Step 4 (outbox validation) to FAIL!
        try (Connection conn = DriverManager.getConnection(SCENARIO_A_JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {

            // Insert Users
            stmt.executeUpdate("INSERT INTO users (id, email, password_hash, full_name, status) VALUES " +
                    "(301, 'orgA@test.ai', '$2a$10$xyz', 'Org A', 'ACTIVE'), " +
                    "(302, 'custA@test.ai', '$2a$10$abc', 'Cust A', 'ACTIVE')");

            // Insert Venues
            stmt.executeUpdate("INSERT INTO venues (id, name, slug, address, city, state, postal_code, capacity) VALUES " +
                    "(1, 'Venue 1', 'mumbai-arena-0378d1', 'BKC', 'Mumbai', 'MH', '400051', 25000), " +
                    "(2, 'Venue 2', 'mumbai-arena-24dc3d', 'BKC', 'Mumbai', 'MH', '400051', 25000)");

            // Insert Events
            stmt.executeUpdate("INSERT INTO events (id, organizer_id, category_id, venue_id, title, slug, short_description, start_time, end_time, base_price, status) VALUES " +
                    "(1, 301, 1, 1, 'Event 1', 'ev-a-1', 'Desc 1', '2026-12-01 10:00:00', '2026-12-01 14:00:00', 1999.00, 'PUBLISHED'), " +
                    "(2, 301, 1, 2, 'Event 2', 'ev-a-2', 'Desc 2', '2026-12-02 10:00:00', '2026-12-02 14:00:00', 1999.00, 'PUBLISHED'), " +
                    "(3, 301, 1, 1, 'Event 3', 'ev-a-3', 'Desc 3', '2026-12-03 10:00:00', '2026-12-03 14:00:00', 2999.00, 'PUBLISHED'), " +
                    "(4, 301, 1, 1, 'Event 4', 'ev-a-4', 'Desc 4', '2026-12-04 10:00:00', '2026-12-04 14:00:00', 2999.00, 'PUBLISHED')");

            // INJECT PARTIAL-FAILURE TRIGGER:
            // Pre-create an incompatible outbox_events table with only 3 columns instead of 14.
            // When V3 runs, Step 3 (index creation & legacy drop) will succeed and commit!
            // Then Step 4 will detect incompatible column count and abort before seating seeding!
            stmt.executeUpdate("CREATE TABLE outbox_events (id BIGINT PRIMARY KEY, event_id VARCHAR(64), payload JSON)");
        }

        // Step 3: Run Flyway Migrate to V3 -> Must FAIL during Step 4!
        Flyway flywayV3A = Flyway.configure()
                .dataSource(SCENARIO_A_JDBC_URL, DB_USER, DB_PASS)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("3"))
                .load();

        Exception failureEx = null;
        try {
            flywayV3A.migrate();
        } catch (Exception ex) {
            failureEx = ex;
        }

        assertNotNull(failureEx, "V3 must fail due to incompatible outbox_events table structure");
        String fullErr = failureEx.toString();
        Throwable c = failureEx.getCause();
        while (c != null) {
            fullErr += " | " + c.toString();
            c = c.getCause();
        }
        assertTrue(fullErr.contains("ERR_OUTBOX_COL_COUNT"), "Error must be ERR_OUTBOX_COL_COUNT");

        // Step 4: Verify Database State AFTER Partial Failure
        try (Connection conn = DriverManager.getConnection(SCENARIO_A_JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {

            // 4.1 Verify composite unique index uk_user_idempotency WAS CREATED and IS COMMITTED
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'bookings' AND index_name = 'uk_user_idempotency'")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "uk_user_idempotency must exist with 2 columns despite later failure");
            }

            // 4.2 Verify legacy single-column index on idempotency_key WAS DROPPED
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'bookings' " +
                    "AND column_name = 'idempotency_key' AND non_unique = 0 AND index_name != 'uk_user_idempotency'")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Legacy single-column index was already dropped before failure");
            }

            // 4.3 Verify composite unique index actively enforces uniqueness
            stmt.executeUpdate("INSERT INTO bookings (id, booking_reference, user_id, event_id, status, total_amount, idempotency_key) VALUES " +
                    "(901, 'BK-TEST-A1', 302, 1, 'CONFIRMED', 100.00, 'key-alpha')");

            // Inserting duplicate (user_id, idempotency_key) must be REJECTED by uk_user_idempotency!
            assertThrows(SQLException.class, () -> {
                stmt.executeUpdate("INSERT INTO bookings (id, booking_reference, user_id, event_id, status, total_amount, idempotency_key) VALUES " +
                        "(902, 'BK-TEST-A2', 302, 1, 'CONFIRMED', 100.00, 'key-alpha')");
            }, "Duplicate (user_id, idempotency_key) must violate uk_user_idempotency");

            // Inserting same idempotency_key for a DIFFERENT user MUST SUCCEED (proving composite behavior!)
            stmt.executeUpdate("INSERT INTO bookings (id, booking_reference, user_id, event_id, status, total_amount, idempotency_key) VALUES " +
                    "(903, 'BK-TEST-A3', 301, 1, 'CONFIRMED', 100.00, 'key-alpha')");

            // 4.4 Verify schema history has version 3 with success = 0
            try (ResultSet rs = stmt.executeQuery("SELECT success FROM flyway_schema_history WHERE version = '3'")) {
                assertTrue(rs.next(), "V3 row must exist in schema history");
                assertFalse(rs.getBoolean("success"), "V3 must have success = 0");
            }

            // 4.5 Verify seating tables were NOT seeded (0 venue sections, 0 physical seats, 0 event seats)
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM venue_sections")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "venue_sections must be unseeded");
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM seats")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "seats must be unseeded");
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM event_seats")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "event_seats must be unseeded");
            }
        }

        // Step 5: Execute flyway.repair() to clear failed schema history status
        flywayV3A.repair();

        // Step 6: Correct the environment failure (drop incompatible outbox table, clean test booking)
        try (Connection conn = DriverManager.getConnection(SCENARIO_A_JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE outbox_events");
            stmt.executeUpdate("DELETE FROM bookings WHERE id IN (901, 903)");
        }

        // Step 7: Re-run Flyway Migrate to V3 -> Must SUCCEED completely!
        MigrateResult retryResultA = flywayV3A.migrate();
        assertTrue(retryResultA.success, "Migration retry after partial DDL repair must succeed");
        assertEquals("3", retryResultA.targetSchemaVersion);

        // Step 8: Verify all schema objects and invariants after recovery
        try (Connection conn = DriverManager.getConnection(SCENARIO_A_JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {

            // Verify outbox_events table created with exactly 14 columns
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'outbox_events'")) {
                assertTrue(rs.next());
                assertEquals(14, rs.getInt(1), "outbox_events must have 14 columns after recovery");
            }

            // Verify seating row counts
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM venue_sections")) {
                assertTrue(rs.next());
                assertEquals(6, rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM seats")) {
                assertTrue(rs.next());
                assertEquals(350, rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM event_seats")) {
                assertTrue(rs.next());
                assertEquals(700, rs.getInt(1));
            }

            // Verify flyway schema history has success = 1
            try (ResultSet rs = stmt.executeQuery("SELECT success FROM flyway_schema_history WHERE version = '3'")) {
                assertTrue(rs.next());
                assertTrue(rs.getBoolean("success"), "V3 must have success = 1 after recovery");
            }
        }
    }

    @Test
    @DisplayName("Scenario B: Failure AFTER Outbox Table Creation, Before Physical Seat Seeding")
    void testFailureAfterOutboxBeforeSeatSeeding() throws Exception {
        // Step 1: Migrate V1 and V2 on SCENARIO_B_DB
        Flyway flywayB = Flyway.configure()
                .dataSource(SCENARIO_B_JDBC_URL, DB_USER, DB_PASS)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("2"))
                .load();

        flywayB.migrate();

        // Step 2: Seed venues, events, users, and INJECT TRIGGER to halt during Step 6 (seats insertion)
        try (Connection conn = DriverManager.getConnection(SCENARIO_B_JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {

            // Insert Users
            stmt.executeUpdate("INSERT INTO users (id, email, password_hash, full_name, status) VALUES " +
                    "(401, 'orgB@test.ai', '$2a$10$xyz', 'Org B', 'ACTIVE'), " +
                    "(402, 'custB@test.ai', '$2a$10$abc', 'Cust B', 'ACTIVE')");

            // Insert Venues
            stmt.executeUpdate("INSERT INTO venues (id, name, slug, address, city, state, postal_code, capacity) VALUES " +
                    "(1, 'Venue 1', 'mumbai-arena-0378d1', 'BKC', 'Mumbai', 'MH', '400051', 25000), " +
                    "(2, 'Venue 2', 'mumbai-arena-24dc3d', 'BKC', 'Mumbai', 'MH', '400051', 25000)");

            // Insert Events
            stmt.executeUpdate("INSERT INTO events (id, organizer_id, category_id, venue_id, title, slug, short_description, start_time, end_time, base_price, status) VALUES " +
                    "(1, 401, 1, 1, 'Event 1', 'ev-b-1', 'Desc 1', '2026-12-01 10:00:00', '2026-12-01 14:00:00', 1999.00, 'PUBLISHED'), " +
                    "(2, 401, 1, 2, 'Event 2', 'ev-b-2', 'Desc 2', '2026-12-02 10:00:00', '2026-12-02 14:00:00', 1999.00, 'PUBLISHED'), " +
                    "(3, 401, 1, 1, 'Event 3', 'ev-b-3', 'Desc 3', '2026-12-03 10:00:00', '2026-12-03 14:00:00', 2999.00, 'PUBLISHED'), " +
                    "(4, 401, 1, 1, 'Event 4', 'ev-b-4', 'Desc 4', '2026-12-04 10:00:00', '2026-12-04 14:00:00', 2999.00, 'PUBLISHED')");

            // INJECT CONTROLLED TRIGGER ON SEATS:
            // This ensures Step 3 (index) and Step 4 (outbox_events creation & validation)
            // and Step 5 (venue_sections) ALL COMMIT successfully!
            // Then Step 6 (seats insertion) fires this trigger and aborts!
            stmt.executeUpdate("CREATE TRIGGER trg_fail_seats BEFORE INSERT ON seats FOR EACH ROW " +
                    "BEGIN " +
                    "    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Controlled failure injected during seat insertion'; " +
                    "END;");
        }

        // Step 3: Run Flyway Migrate to V3 -> Must FAIL during Step 6!
        Flyway flywayV3B = Flyway.configure()
                .dataSource(SCENARIO_B_JDBC_URL, DB_USER, DB_PASS)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("3"))
                .load();

        Exception failureExB = null;
        try {
            flywayV3B.migrate();
        } catch (Exception ex) {
            failureExB = ex;
        }

        assertNotNull(failureExB, "V3 must fail during seat insertion trigger");
        String fullErrB = failureExB.toString();
        Throwable cB = failureExB.getCause();
        while (cB != null) {
            fullErrB += " | " + cB.toString();
            cB = cB.getCause();
        }
        assertTrue(fullErrB.contains("Controlled failure injected during seat insertion"));

        // Step 4: Verify Database State AFTER Partial Failure in Scenario B
        try (Connection conn = DriverManager.getConnection(SCENARIO_B_JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {

            // 4.1 Verify uk_user_idempotency IS COMMITTED
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = 'bookings' AND index_name = 'uk_user_idempotency'")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }

            // 4.2 Verify outbox_events WAS FULLY CREATED and COMMITTED
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT count(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'outbox_events'")) {
                assertTrue(rs.next());
                assertEquals(14, rs.getInt(1), "outbox_events must be fully created and committed");
            }

            // 4.3 Verify venue_sections was rolled back by Flyway transaction rollback (DML is transactional)
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM venue_sections")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "venue_sections DML rolled back by transaction on failure");
            }

            // 4.4 Verify seats and event_seats are 0 (not seeded)
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM seats")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM event_seats")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }

            // 4.5 Verify schema history has version 3 with success = 0
            try (ResultSet rs = stmt.executeQuery("SELECT success FROM flyway_schema_history WHERE version = '3'")) {
                assertTrue(rs.next());
                assertFalse(rs.getBoolean("success"));
            }
        }

        // Step 5: Execute flyway.repair()
        flywayV3B.repair();

        // Step 6: Drop the injected failure trigger
        try (Connection conn = DriverManager.getConnection(SCENARIO_B_JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TRIGGER trg_fail_seats");
        }

        // Step 7: Re-run Flyway Migrate to V3 -> Must SUCCEED completely!
        MigrateResult retryResultB = flywayV3B.migrate();
        assertTrue(retryResultB.success, "Migration retry after seat trigger repair must succeed");
        assertEquals("3", retryResultB.targetSchemaVersion);

        // Step 8: Verify all schema objects after recovery
        try (Connection conn = DriverManager.getConnection(SCENARIO_B_JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM venue_sections")) {
                assertTrue(rs.next());
                assertEquals(6, rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM seats")) {
                assertTrue(rs.next());
                assertEquals(350, rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM event_seats")) {
                assertTrue(rs.next());
                assertEquals(700, rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT success FROM flyway_schema_history WHERE version = '3'")) {
                assertTrue(rs.next());
                assertTrue(rs.getBoolean("success"));
            }
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
