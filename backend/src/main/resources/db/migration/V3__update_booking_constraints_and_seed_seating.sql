-- ============================================================================
-- V3__update_booking_constraints_and_seed_seating.sql
-- SeatSync AI: Phase 5.1 Seating Foundation, Idempotency & Outbox Migration
-- Engine: MySQL 8.0 (InnoDB) | Flyway 10.x Compatible
-- ============================================================================

-- ----------------------------------------------------------------------------
-- STEP 1: RESOLVE INTENDED VENUES BY UNIQUE NATURAL KEY (SLUG)
-- ----------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS intended_venues;
CREATE TEMPORARY TABLE intended_venues (
    venue_id BIGINT PRIMARY KEY,
    venue_slug VARCHAR(180) NOT NULL UNIQUE,
    venue_name VARCHAR(150) NOT NULL
);

INSERT INTO intended_venues (venue_id, venue_slug, venue_name)
SELECT id, slug, name
FROM venues
WHERE slug IN ('mumbai-arena-0378d1', 'mumbai-arena-24dc3d');

-- ----------------------------------------------------------------------------
-- STEP 2: PREFLIGHT VALIDATION (Pure Session & Diagnostic Checks)
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS preflight_validation_v3;
DELIMITER //
CREATE PROCEDURE preflight_validation_v3()
BEGIN
    DECLARE resolved_venues INT DEFAULT 0;
    DECLARE dupe_bookings INT DEFAULT 0;
    DECLARE existing_uk_count INT DEFAULT 0;
    DECLARE col1_valid INT DEFAULT 0;
    DECLARE col2_valid INT DEFAULT 0;
    DECLARE null_section_fields INT DEFAULT 0;
    DECLARE section_conflicts INT DEFAULT 0;
    DECLARE invalid_event_prices INT DEFAULT 0;
    DECLARE booked_seat_violations INT DEFAULT 0;
    DECLARE held_seat_violations INT DEFAULT 0;
    DECLARE available_seat_violations INT DEFAULT 0;
    DECLARE orphan_violations INT DEFAULT 0;
    DECLARE dupe_es_count INT DEFAULT 0;
    DECLARE invalid_seat_prices INT DEFAULT 0;
    DECLARE fk_dep_count INT DEFAULT 0;

    -- 2.1 Verify exactly 2 intended venues resolved by slug
    SELECT COUNT(*) INTO resolved_venues FROM intended_venues;
    IF resolved_venues != 2 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Preflight failed [ERR_VENUES]: Could not resolve exactly 2 intended venues by slug (mumbai-arena-0378d1, mumbai-arena-24dc3d).';
    END IF;

    -- 2.2 Verify zero duplicate (user_id, idempotency_key) records in bookings
    SELECT COUNT(*) INTO dupe_bookings
    FROM (
        SELECT user_id, idempotency_key
        FROM bookings
        WHERE idempotency_key IS NOT NULL
        GROUP BY user_id, idempotency_key
        HAVING COUNT(*) > 1
    ) dupes;

    IF dupe_bookings > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Preflight failed [ERR_DUPE_IDEMPOTENCY]: Duplicate (user_id, idempotency_key) records exist in bookings. Migration aborted before schema modifications.';
    END IF;

    -- 2.3 Verify if uk_user_idempotency exists, it has strictly 2 columns in exact order
    SELECT 
        COUNT(*),
        IFNULL(SUM(CASE WHEN seq_in_index = 1 AND column_name = 'user_id' AND non_unique = 0 AND sub_part IS NULL THEN 1 ELSE 0 END), 0),
        IFNULL(SUM(CASE WHEN seq_in_index = 2 AND column_name = 'idempotency_key' AND non_unique = 0 AND sub_part IS NULL THEN 1 ELSE 0 END), 0)
    INTO existing_uk_count, col1_valid, col2_valid
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND index_name = 'uk_user_idempotency';

    IF existing_uk_count > 0 AND (existing_uk_count != 2 OR col1_valid != 1 OR col2_valid != 1) THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Preflight failed [ERR_INVALID_COMPOSITE]: Existing index uk_user_idempotency has invalid column structure, sequence, or prefixing.';
    END IF;

    -- 2.4 Verify all events belonging to intended venues have positive base_price
    SELECT COUNT(*) INTO invalid_event_prices
    FROM events e
    JOIN intended_venues iv ON e.venue_id = iv.venue_id
    WHERE e.base_price IS NULL OR e.base_price <= 0.00;

    IF invalid_event_prices > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Preflight failed [ERR_EVENT_BASE_PRICE]: One or more events at intended venues have a NULL or non-positive base_price.';
    END IF;

    -- 2.5 Verify no NULL values in existing section fields for intended venues
    SELECT COUNT(*) INTO null_section_fields
    FROM venue_sections vs
    JOIN intended_venues iv ON vs.venue_id = iv.venue_id
    WHERE vs.capacity IS NULL 
       OR vs.tier_level IS NULL 
       OR vs.display_order IS NULL 
       OR vs.name IS NULL 
       OR vs.section_code IS NULL;

    IF null_section_fields > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Preflight failed [ERR_SECTION_NULL]: Existing venue_sections contain NULL values in required fields.';
    END IF;

    -- 2.6 Verify complete section metadata invariants for intended venues
    SELECT COUNT(*) INTO section_conflicts
    FROM venue_sections vs
    JOIN intended_venues iv ON vs.venue_id = iv.venue_id
    WHERE 
        vs.section_code NOT IN ('VIP', 'PLATINUM', 'GOLD')
        OR (vs.section_code = 'VIP' AND (vs.capacity != 40 OR vs.tier_level != 1 OR vs.display_order != 1 OR vs.name != 'VIP Premium Club'))
        OR (vs.section_code = 'PLATINUM' AND (vs.capacity != 60 OR vs.tier_level != 2 OR vs.display_order != 2 OR vs.name != 'Platinum Lower Bowl'))
        OR (vs.section_code = 'GOLD' AND (vs.capacity != 75 OR vs.tier_level != 3 OR vs.display_order != 3 OR vs.name != 'Gold Grand Tier'));

    IF section_conflicts > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Preflight failed [ERR_SECTION_METADATA]: Conflicting venue_sections metadata detected. Expected VIP (40, tier 1, order 1), PLATINUM (60, tier 2, order 2), GOLD (75, tier 3, order 3).';
    END IF;

    -- 2.7 Verify existing BOOKED seats consistency (confirmed booking, same event, existing user, null hold fields)
    SELECT COUNT(*) INTO booked_seat_violations
    FROM event_seats es
    JOIN events e ON es.event_id = e.id
    JOIN intended_venues iv ON e.venue_id = iv.venue_id
    LEFT JOIN bookings b ON es.current_booking_id = b.id
    LEFT JOIN users u ON b.user_id = u.id
    WHERE es.status = 'BOOKED' 
      AND (
          es.current_booking_id IS NULL 
          OR b.id IS NULL 
          OR b.status != 'CONFIRMED'
          OR b.event_id != es.event_id
          OR u.id IS NULL
          OR es.held_by_user_id IS NOT NULL
          OR es.hold_token IS NOT NULL
          OR es.held_until IS NOT NULL
      );

    IF booked_seat_violations > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Preflight failed [ERR_BOOKED_SEAT_CONSISTENCY]: BOOKED event_seats contain invalid booking references, status, event mismatch, or lingering hold fields.';
    END IF;

    -- 2.8 Verify existing HELD seats consistency (valid user, non-empty token, future expiry, null booking)
    SELECT COUNT(*) INTO held_seat_violations
    FROM event_seats es
    JOIN events e ON es.event_id = e.id
    JOIN intended_venues iv ON e.venue_id = iv.venue_id
    LEFT JOIN users u ON es.held_by_user_id = u.id
    WHERE es.status = 'HELD' 
      AND (
          es.held_by_user_id IS NULL 
          OR u.id IS NULL 
          OR es.hold_token IS NULL 
          OR es.hold_token = '' 
          OR es.held_until IS NULL 
          OR es.held_until <= NOW()
          OR es.current_booking_id IS NOT NULL
      );

    IF held_seat_violations > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Preflight failed [ERR_HELD_SEAT_CONSISTENCY]: HELD event_seats contain corrupted, missing, or expired hold data.';
    END IF;

    -- 2.9 Verify existing AVAILABLE seats cleanliness (strictly null booking and hold fields)
    SELECT COUNT(*) INTO available_seat_violations
    FROM event_seats es
    JOIN events e ON es.event_id = e.id
    JOIN intended_venues iv ON e.venue_id = iv.venue_id
    WHERE es.status = 'AVAILABLE' 
      AND (
          es.current_booking_id IS NOT NULL 
          OR es.held_by_user_id IS NOT NULL 
          OR es.hold_token IS NOT NULL 
          OR es.held_until IS NOT NULL
      );

    IF available_seat_violations > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Preflight failed [ERR_AVAILABLE_SEAT_DIRTY]: AVAILABLE event_seats have non-null booking or hold fields.';
    END IF;

    -- 2.10 Verify zero duplicate candidate groupings in existing event_seats
    SELECT COUNT(*) INTO dupe_es_count
    FROM (
        SELECT es.event_id, es.seat_id
        FROM event_seats es
        JOIN events e ON es.event_id = e.id
        JOIN intended_venues iv ON e.venue_id = iv.venue_id
        GROUP BY es.event_id, es.seat_id
        HAVING COUNT(*) > 1
    ) dupes;

    IF dupe_es_count > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Preflight failed [ERR_DUPE_EVENT_SEAT]: Duplicate (event_id, seat_id) records found in existing event_seats.';
    END IF;

    -- 2.11 Verify zero orphaned or cross-venue records across 6 categories
    SELECT COUNT(*) INTO orphan_violations
    FROM (
        -- Category A: Event Seats missing Events
        SELECT es.id FROM event_seats es LEFT JOIN events e ON es.event_id = e.id WHERE e.id IS NULL
        UNION ALL
        -- Category B: Event Seats missing Physical Seats
        SELECT es.id FROM event_seats es LEFT JOIN seats s ON es.seat_id = s.id WHERE s.id IS NULL
        UNION ALL
        -- Category C: Physical Seats missing Venue Sections
        SELECT s.id FROM seats s LEFT JOIN venue_sections vs ON s.venue_section_id = vs.id WHERE vs.id IS NULL
        UNION ALL
        -- Category D: Venue Sections missing Venues
        SELECT vs.id FROM venue_sections vs LEFT JOIN venues v ON vs.venue_id = v.id WHERE v.id IS NULL
        UNION ALL
        -- Category E: Events missing Venues
        SELECT e.id FROM events e LEFT JOIN venues v ON e.venue_id = v.id WHERE v.id IS NULL
        UNION ALL
        -- Category F: Cross-Venue Event-Seat Link
        SELECT es.id FROM event_seats es 
        JOIN events e ON es.event_id = e.id 
        JOIN seats s ON es.seat_id = s.id 
        JOIN venue_sections vs ON s.venue_section_id = vs.id 
        WHERE e.venue_id != vs.venue_id
    ) orphans;

    IF orphan_violations > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Preflight failed [ERR_ORPHAN_OR_CROSS_VENUE]: Broken foreign keys or cross-venue seat associations detected.';
    END IF;

    -- 2.12 Verify no existing event_seats have non-positive or NULL prices
    SELECT COUNT(*) INTO invalid_seat_prices
    FROM event_seats es
    JOIN events e ON es.event_id = e.id
    JOIN intended_venues iv ON e.venue_id = iv.venue_id
    WHERE es.price IS NULL OR es.price <= 0.00;

    IF invalid_seat_prices > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Preflight failed [ERR_SEAT_PRICE_INVALID]: One or more existing event_seats have NULL or non-positive price.';
    END IF;

    -- 2.13 Verify zero foreign-key dependencies reference bookings(idempotency_key)
    SELECT COUNT(*) INTO fk_dep_count
    FROM information_schema.KEY_COLUMN_USAGE kcu
    JOIN information_schema.REFERENTIAL_CONSTRAINTS rc 
      ON kcu.CONSTRAINT_NAME = rc.CONSTRAINT_NAME 
     AND kcu.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
    WHERE kcu.REFERENCED_TABLE_SCHEMA = DATABASE()
      AND kcu.REFERENCED_TABLE_NAME = 'bookings'
      AND kcu.REFERENCED_COLUMN_NAME = 'idempotency_key';

    IF fk_dep_count > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Preflight failed [ERR_FK_DEPENDENCY]: A foreign key constraint references bookings(idempotency_key). Cannot drop legacy index without breaking dependent foreign key. Solution: Retarget referencing foreign keys before migrating.';
    END IF;
END //
DELIMITER ;

CALL preflight_validation_v3();
DROP PROCEDURE IF EXISTS preflight_validation_v3;

-- ----------------------------------------------------------------------------
-- STEP 3: SAFE IDEMPOTENCY INDEX REPLACEMENT (ADD-BEFORE-DROP)
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS safely_replace_idempotency_indexes;
DELIMITER //
CREATE PROCEDURE safely_replace_idempotency_indexes()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE idx_to_drop VARCHAR(64);
    DECLARE uk_exists INT DEFAULT 0;
    DECLARE col1_valid INT DEFAULT 0;
    DECLARE col2_valid INT DEFAULT 0;
    DECLARE dupe_count INT DEFAULT 0;
    DECLARE fk_dep_count INT DEFAULT 0;
    DECLARE idx_fk_count INT DEFAULT 0;

    -- Cursor to iterate over EVERY qualifying legacy single-column unique index on idempotency_key
    DECLARE old_index_cursor CURSOR FOR
        SELECT s.index_name
        FROM information_schema.statistics s
        WHERE s.table_schema = DATABASE()
          AND s.table_name = 'bookings'
          AND s.column_name = 'idempotency_key'
          AND s.non_unique = 0
          AND s.sub_part IS NULL
          AND s.index_name != 'uk_user_idempotency'
        GROUP BY s.index_name
        HAVING COUNT(*) = 1 AND MAX(s.seq_in_index) = 1;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    -- Step 3.1: Pre-inspect uk_user_idempotency BEFORE touching any index
    SELECT 
        COUNT(*),
        IFNULL(SUM(CASE WHEN seq_in_index = 1 AND column_name = 'user_id' AND non_unique = 0 AND sub_part IS NULL THEN 1 ELSE 0 END), 0),
        IFNULL(SUM(CASE WHEN seq_in_index = 2 AND column_name = 'idempotency_key' AND non_unique = 0 AND sub_part IS NULL THEN 1 ELSE 0 END), 0)
    INTO uk_exists, col1_valid, col2_valid
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND index_name = 'uk_user_idempotency';

    IF uk_exists > 0 AND (uk_exists != 2 OR col1_valid != 1 OR col2_valid != 1) THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Transition aborted [ERR_INVALID_COMPOSITE]: uk_user_idempotency exists with invalid columns, sequence, or prefixing. Existing indexes preserved.';
    END IF;

    -- Step 3.2: Duplicate Pre-check BEFORE touching any index
    SELECT COUNT(*) INTO dupe_count
    FROM (
        SELECT user_id, idempotency_key
        FROM bookings
        WHERE idempotency_key IS NOT NULL
        GROUP BY user_id, idempotency_key
        HAVING COUNT(*) > 1
    ) dupes;

    IF dupe_count > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Transition aborted [ERR_DUPLICATE_IDEMPOTENCY]: Duplicate (user_id, idempotency_key) records exist in bookings. Existing indexes preserved.';
    END IF;

    -- Step 3.3: Pre-check Foreign-Key Dependencies on bookings(idempotency_key)
    SELECT COUNT(*) INTO fk_dep_count
    FROM information_schema.KEY_COLUMN_USAGE kcu
    JOIN information_schema.REFERENTIAL_CONSTRAINTS rc 
      ON kcu.CONSTRAINT_NAME = rc.CONSTRAINT_NAME 
     AND kcu.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
    WHERE kcu.REFERENCED_TABLE_SCHEMA = DATABASE()
      AND kcu.REFERENCED_TABLE_NAME = 'bookings'
      AND kcu.REFERENCED_COLUMN_NAME = 'idempotency_key';

    IF fk_dep_count > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Transition aborted [ERR_FK_DEPENDENCY]: A foreign key constraint references bookings(idempotency_key). Cannot drop legacy index without breaking dependent foreign key constraints. Safe solution: Drop or retarget referencing foreign keys before migrating.';
    END IF;

    -- Step 3.4: CREATE the new composite unique index FIRST (if absent)
    IF uk_exists = 0 THEN
        ALTER TABLE bookings ADD CONSTRAINT uk_user_idempotency UNIQUE (user_id, idempotency_key);
    END IF;

    -- Step 3.5: VERIFY the new composite unique index BEFORE dropping legacy indexes
    SELECT 
        COUNT(*),
        IFNULL(SUM(CASE WHEN seq_in_index = 1 AND column_name = 'user_id' AND non_unique = 0 AND sub_part IS NULL THEN 1 ELSE 0 END), 0),
        IFNULL(SUM(CASE WHEN seq_in_index = 2 AND column_name = 'idempotency_key' AND non_unique = 0 AND sub_part IS NULL THEN 1 ELSE 0 END), 0)
    INTO uk_exists, col1_valid, col2_valid
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'bookings'
      AND index_name = 'uk_user_idempotency';

    IF uk_exists != 2 OR col1_valid != 1 OR col2_valid != 1 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Transition failed [ERR_COMPOSITE_VERIFY]: uk_user_idempotency verification failed after creation. Legacy indexes preserved.';
    END IF;

    -- Step 3.6: ONLY AFTER successful verification, drop qualifying legacy single-column unique indexes
    OPEN old_index_cursor;
    drop_loop: LOOP
        FETCH old_index_cursor INTO idx_to_drop;
        IF done THEN
            LEAVE drop_loop;
        END IF;

        -- Verify no foreign key depends on this specific legacy index
        SELECT COUNT(*) INTO idx_fk_count
        FROM information_schema.REFERENTIAL_CONSTRAINTS rc
        WHERE rc.CONSTRAINT_SCHEMA = DATABASE()
          AND rc.REFERENCED_TABLE_NAME = 'bookings'
          AND rc.UNIQUE_CONSTRAINT_NAME = idx_to_drop;

        IF idx_fk_count > 0 THEN
            SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Transition aborted [ERR_FK_INDEX_DEPENDENCY]: A foreign key constraint depends directly on the unique index being dropped. Safe solution: Drop or retarget referencing foreign key first.';
        END IF;

        SET @drop_stmt = CONCAT('ALTER TABLE bookings DROP INDEX `', idx_to_drop, '`;');
        PREPARE stmt_exec FROM @drop_stmt;
        EXECUTE stmt_exec;
        DEALLOCATE PREPARE stmt_exec;
    END LOOP;
    CLOSE old_index_cursor;
END //
DELIMITER ;

CALL safely_replace_idempotency_indexes();
DROP PROCEDURE IF EXISTS safely_replace_idempotency_indexes;

-- ----------------------------------------------------------------------------
-- STEP 4: VALIDATE OR CREATE OUTBOX EVENTS TABLE
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS validate_or_create_outbox_table;
DELIMITER //
CREATE PROCEDURE validate_or_create_outbox_table()
BEGIN
    DECLARE tbl_exists INT DEFAULT 0;
    DECLARE total_col_count INT DEFAULT 0;
    DECLARE valid_col_defs INT DEFAULT 0;
    DECLARE pk_valid INT DEFAULT 0;
    DECLARE uk_event_id_valid INT DEFAULT 0;
    DECLARE other_uk_count INT DEFAULT 0;
    DECLARE prefix_idx_count INT DEFAULT 0;
    DECLARE claim_idx_valid INT DEFAULT 0;
    DECLARE agg_idx_valid INT DEFAULT 0;
    DECLARE total_canonical_idx INT DEFAULT 0;
    DECLARE total_actual_idx INT DEFAULT 0;
    DECLARE total_idx_rows INT DEFAULT 0;

    -- Check if table exists
    SELECT COUNT(*) INTO tbl_exists
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'outbox_events';

    IF tbl_exists = 0 THEN
        CREATE TABLE outbox_events (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            event_id VARCHAR(64) NOT NULL,
            aggregate_type VARCHAR(50) NOT NULL,
            aggregate_id VARCHAR(100) NOT NULL,
            event_type VARCHAR(100) NOT NULL,
            payload JSON NOT NULL,
            status ENUM('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED') NOT NULL DEFAULT 'PENDING',
            retry_count INT UNSIGNED NOT NULL DEFAULT 0,
            claim_token VARCHAR(64) NULL,
            claimed_at TIMESTAMP NULL,
            next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_error_message TEXT NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            CONSTRAINT uk_outbox_event_id UNIQUE (event_id),
            INDEX idx_outbox_claim (status, next_attempt_at, retry_count, claimed_at),
            INDEX idx_outbox_aggregate (aggregate_type, aggregate_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;

    -- 4.1 Assert EXACTLY 14 columns exist (no extraneous, no missing)
    SELECT COUNT(*) INTO total_col_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'outbox_events';

    IF total_col_count != 14 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Validation failed [ERR_OUTBOX_COL_COUNT]: outbox_events must have exactly 14 columns.';
    END IF;

    -- 4.2 Assert all 14 column definitions, types, nullability, defaults, and auto-update attributes
    SELECT COUNT(*) INTO valid_col_defs
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'outbox_events'
      AND (
          (column_name = 'id' AND data_type = 'bigint' AND is_nullable = 'NO' AND extra LIKE '%auto_increment%')
          OR (column_name = 'event_id' AND data_type = 'varchar' AND character_maximum_length = 64 AND is_nullable = 'NO')
          OR (column_name = 'aggregate_type' AND data_type = 'varchar' AND character_maximum_length = 50 AND is_nullable = 'NO')
          OR (column_name = 'aggregate_id' AND data_type = 'varchar' AND character_maximum_length = 100 AND is_nullable = 'NO')
          OR (column_name = 'event_type' AND data_type = 'varchar' AND character_maximum_length = 100 AND is_nullable = 'NO')
          OR (column_name = 'payload' AND data_type = 'json' AND is_nullable = 'NO')
          OR (column_name = 'status' AND column_type = "enum('PENDING','PROCESSING','PUBLISHED','FAILED')" AND is_nullable = 'NO' AND column_default = 'PENDING')
          OR (column_name = 'retry_count' AND data_type = 'int' AND column_type LIKE '%unsigned%' AND is_nullable = 'NO' AND column_default = '0')
          OR (column_name = 'claim_token' AND data_type = 'varchar' AND character_maximum_length = 64 AND is_nullable = 'YES' AND column_default IS NULL)
          OR (column_name = 'claimed_at' AND data_type = 'timestamp' AND is_nullable = 'YES' AND column_default IS NULL)
          OR (column_name = 'next_attempt_at' AND data_type = 'timestamp' AND is_nullable = 'NO' AND column_default LIKE '%CURRENT_TIMESTAMP%')
          OR (column_name = 'last_error_message' AND data_type = 'text' AND is_nullable = 'YES' AND column_default IS NULL)
          OR (column_name = 'created_at' AND data_type = 'timestamp' AND is_nullable = 'NO' AND column_default LIKE '%CURRENT_TIMESTAMP%' AND extra NOT LIKE '%on update%')
          OR (column_name = 'updated_at' AND data_type = 'timestamp' AND is_nullable = 'NO' AND column_default LIKE '%CURRENT_TIMESTAMP%' AND extra LIKE '%on update CURRENT_TIMESTAMP%')
      );

    IF valid_col_defs != 14 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Validation failed [ERR_OUTBOX_COL_DEFS]: outbox_events columns have invalid types, nullability, or defaults.';
    END IF;

    -- 4.3 Assert PRIMARY key is strictly one column: id, with sub_part IS NULL
    SELECT (
        COUNT(*) = 1 
        AND MAX(column_name) = 'id' 
        AND MAX(seq_in_index) = 1 
        AND MAX(sub_part) IS NULL 
        AND MAX(non_unique) = 0
    ) INTO pk_valid
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'outbox_events'
      AND index_name = 'PRIMARY';

    IF pk_valid != 1 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Validation failed [ERR_OUTBOX_PK]: PRIMARY key must be strictly on id with no prefixing.';
    END IF;

    -- 4.4 Assert exact UNIQUE index named uk_outbox_event_id on event_id with sub_part IS NULL
    SELECT (
        COUNT(*) = 1 
        AND MAX(column_name) = 'event_id' 
        AND MAX(seq_in_index) = 1 
        AND MAX(non_unique) = 0 
        AND MAX(sub_part) IS NULL
    ) INTO uk_event_id_valid
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'outbox_events'
      AND index_name = 'uk_outbox_event_id';

    IF uk_event_id_valid != 1 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Validation failed [ERR_OUTBOX_UK_NAME]: Missing exact single-column UNIQUE index uk_outbox_event_id on event_id.';
    END IF;

    -- 4.5 Reject ANY other unexpected or duplicate unique index on event_id
    SELECT COUNT(DISTINCT index_name) INTO other_uk_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'outbox_events'
      AND column_name = 'event_id'
      AND non_unique = 0
      AND index_name != 'uk_outbox_event_id';

    IF other_uk_count > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Validation failed [ERR_OUTBOX_DUPE_UK]: Unexpected duplicate unique index on event_id detected.';
    END IF;

    -- 4.6 Verify NO prefix indexes exist on outbox_events (sub_part IS NULL across all index columns)
    SELECT COUNT(*) INTO prefix_idx_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'outbox_events'
      AND sub_part IS NOT NULL;

    IF prefix_idx_count > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Validation failed [ERR_OUTBOX_PREFIX_INDEX]: Prefix indexes are prohibited on outbox_events.';
    END IF;

    -- 4.7 Verify idx_outbox_claim index sequence, uniqueness, and sub_part
    SELECT (
        COUNT(*) = 4 
        AND SUM(CASE WHEN seq_in_index = 1 AND column_name = 'status' AND sub_part IS NULL THEN 1 ELSE 0 END) = 1
        AND SUM(CASE WHEN seq_in_index = 2 AND column_name = 'next_attempt_at' AND sub_part IS NULL THEN 1 ELSE 0 END) = 1
        AND SUM(CASE WHEN seq_in_index = 3 AND column_name = 'retry_count' AND sub_part IS NULL THEN 1 ELSE 0 END) = 1
        AND SUM(CASE WHEN seq_in_index = 4 AND column_name = 'claimed_at' AND sub_part IS NULL THEN 1 ELSE 0 END) = 1
        AND MAX(non_unique) = 1
    ) INTO claim_idx_valid
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'outbox_events'
      AND index_name = 'idx_outbox_claim';

    IF claim_idx_valid != 1 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Validation failed [ERR_OUTBOX_CLAIM_IDX]: idx_outbox_claim has incorrect column sequence or prefixing.';
    END IF;

    -- 4.8 Verify idx_outbox_aggregate index sequence, uniqueness, and sub_part
    SELECT (
        COUNT(*) = 2 
        AND SUM(CASE WHEN seq_in_index = 1 AND column_name = 'aggregate_type' AND sub_part IS NULL THEN 1 ELSE 0 END) = 1
        AND SUM(CASE WHEN seq_in_index = 2 AND column_name = 'aggregate_id' AND sub_part IS NULL THEN 1 ELSE 0 END) = 1
        AND MAX(non_unique) = 1
    ) INTO agg_idx_valid
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'outbox_events'
      AND index_name = 'idx_outbox_aggregate';

    IF agg_idx_valid != 1 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Validation failed [ERR_OUTBOX_AGG_IDX]: idx_outbox_aggregate has incorrect column sequence or prefixing.';
    END IF;

    -- 4.9 Verify exact canonical index names and total count
    SELECT COUNT(DISTINCT index_name) INTO total_canonical_idx
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'outbox_events'
      AND index_name IN ('PRIMARY', 'uk_outbox_event_id', 'idx_outbox_claim', 'idx_outbox_aggregate');

    SELECT COUNT(DISTINCT index_name), COUNT(*) INTO total_actual_idx, total_idx_rows
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'outbox_events';

    IF total_canonical_idx != 4 OR total_actual_idx != 4 OR total_idx_rows != 8 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Validation failed [ERR_OUTBOX_INDEX_COUNT]: outbox_events has unexpected extraneous, missing, or misnamed indexes.';
    END IF;
END //
DELIMITER ;

CALL validate_or_create_outbox_table();
DROP PROCEDURE IF EXISTS validate_or_create_outbox_table;

-- ----------------------------------------------------------------------------
-- STEP 5: SEED VENUE SECTIONS (Dynamically Bound to Intended Venues)
-- ----------------------------------------------------------------------------
INSERT INTO venue_sections (venue_id, name, section_code, tier_level, capacity, display_order)
SELECT iv.venue_id, sec.name, sec.section_code, sec.tier_level, sec.capacity, sec.display_order
FROM intended_venues iv
CROSS JOIN (
    SELECT 'VIP Premium Club' AS name, 'VIP' AS section_code, 1 AS tier_level, 40 AS capacity, 1 AS display_order
    UNION ALL
    SELECT 'Platinum Lower Bowl' AS name, 'PLATINUM' AS section_code, 2 AS tier_level, 60 AS capacity, 2 AS display_order
    UNION ALL
    SELECT 'Gold Grand Tier' AS name, 'GOLD' AS section_code, 3 AS tier_level, 75 AS capacity, 3 AS display_order
) sec
WHERE iv.venue_id IS NOT NULL
ON DUPLICATE KEY UPDATE venue_sections.id = venue_sections.id;

-- ----------------------------------------------------------------------------
-- STEP 6: SEED PHYSICAL SEATS (Pure Join-Based, Zero Hardcoded Section IDs)
-- ----------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS temp_numbers;
CREATE TEMPORARY TABLE temp_numbers (n INT PRIMARY KEY);
INSERT INTO temp_numbers (n) VALUES 
(1),(2),(3),(4),(5),(6),(7),(8),(9),(10),(11),(12),(13),(14),(15),(16),(17),(18),(19),(20),(21),(22),(23),(24),(25);

-- 6.1 VIP: Rows A, B (20 seats each = 40 seats per venue)
INSERT INTO seats (venue_section_id, row_label, seat_number, grid_x, grid_y, is_accessible)
SELECT 
    vs.id, r.row_label, tn.n, tn.n AS grid_x,
    IF(r.row_label = 'A', 1, 2) AS grid_y,
    IF(tn.n IN (1, 20), TRUE, FALSE) AS is_accessible
FROM venue_sections vs
JOIN intended_venues iv ON vs.venue_id = iv.venue_id
JOIN (SELECT 'A' AS row_label UNION ALL SELECT 'B') r
CROSS JOIN temp_numbers tn
WHERE vs.section_code = 'VIP' AND tn.n <= 20
ON DUPLICATE KEY UPDATE seats.id = seats.id;

-- 6.2 PLATINUM: Rows C, D, E (20 seats each = 60 seats per venue)
INSERT INTO seats (venue_section_id, row_label, seat_number, grid_x, grid_y, is_accessible)
SELECT 
    vs.id, r.row_label, tn.n, tn.n AS grid_x,
    CASE r.row_label WHEN 'C' THEN 3 WHEN 'D' THEN 4 WHEN 'E' THEN 5 END AS grid_y,
    IF(tn.n IN (1, 20), TRUE, FALSE) AS is_accessible
FROM venue_sections vs
JOIN intended_venues iv ON vs.venue_id = iv.venue_id
JOIN (SELECT 'C' AS row_label UNION ALL SELECT 'D' UNION ALL SELECT 'E') r
CROSS JOIN temp_numbers tn
WHERE vs.section_code = 'PLATINUM' AND tn.n <= 20
ON DUPLICATE KEY UPDATE seats.id = seats.id;

-- 6.3 GOLD: Rows F, G, H (25 seats each = 75 seats per venue)
INSERT INTO seats (venue_section_id, row_label, seat_number, grid_x, grid_y, is_accessible)
SELECT 
    vs.id, r.row_label, tn.n, tn.n AS grid_x,
    CASE r.row_label WHEN 'F' THEN 6 WHEN 'G' THEN 7 WHEN 'H' THEN 8 END AS grid_y,
    IF(tn.n IN (1, 25), TRUE, FALSE) AS is_accessible
FROM venue_sections vs
JOIN intended_venues iv ON vs.venue_id = iv.venue_id
JOIN (SELECT 'F' AS row_label UNION ALL SELECT 'G' UNION ALL SELECT 'H') r
CROSS JOIN temp_numbers tn
WHERE vs.section_code = 'GOLD' AND tn.n <= 25
ON DUPLICATE KEY UPDATE seats.id = seats.id;

DROP TEMPORARY TABLE temp_numbers;

-- ----------------------------------------------------------------------------
-- STEP 7: SEED EVENT SEATS (Scoped strictly to each Event's Hosting Venue)
-- Preserves existing BOOKED and HELD records; updates price for AVAILABLE seats
-- ----------------------------------------------------------------------------
INSERT INTO event_seats (event_id, seat_id, status, price, version)
SELECT 
    e.id AS event_id,
    s.id AS seat_id,
    'AVAILABLE' AS status,
    CASE 
        WHEN vs.section_code = 'VIP' THEN ROUND(e.base_price * 2.50, 2)
        WHEN vs.section_code = 'PLATINUM' THEN ROUND(e.base_price * 1.75, 2)
        ELSE ROUND(e.base_price * 1.00, 2)
    END AS price,
    0 AS version
FROM events e
JOIN intended_venues iv ON e.venue_id = iv.venue_id
JOIN venue_sections vs ON vs.venue_id = iv.venue_id
JOIN seats s ON s.venue_section_id = vs.id
ON DUPLICATE KEY UPDATE 
    event_seats.price = IF(event_seats.status = 'AVAILABLE', VALUES(price), event_seats.price);

-- Clean up session temporary table
DROP TEMPORARY TABLE IF EXISTS intended_venues;
