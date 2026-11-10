-- SeatSync AI: V2 Seed Data
-- Seeds default authorization roles, default administrator, and initial event categories

-- Seed default roles
INSERT INTO roles (name, description) VALUES
('ROLE_CUSTOMER', 'Standard user who can browse events, hold seats, and book tickets'),
('ROLE_ORGANIZER', 'Event organizer who can create and manage events, venues, and view sales metrics'),
('ROLE_ADMIN', 'Platform administrator with full system-wide permissions and audit access')
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- Seed default administrator (Password: Admin@SeatSync2026!)
-- BCrypt hash generated with strength 10
INSERT INTO users (email, password_hash, full_name, phone_number, status) VALUES
('admin@seatsync.ai', '$2a$10$qR6K3hZ1q8wY9kX0vJ5u3eP4oG7mN2bV8cT1xS4zW7yA3dE6fI9uO', 'SeatSync System Administrator', '+919999999999', 'ACTIVE')
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name);

-- Assign ROLE_ADMIN to default administrator
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'admin@seatsync.ai' AND r.name = 'ROLE_ADMIN'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- Seed initial event categories
INSERT INTO event_categories (name, slug, description, icon_name, display_order, is_active) VALUES
('Concerts & Music', 'concerts-music', 'Live music concerts, indie artists, orchestra, and music festivals', 'Music', 1, TRUE),
('Sports & Fitness', 'sports-fitness', 'Cricket, football, marathon, badminton, and arena sports', 'Trophy', 2, TRUE),
('Stand-up Comedy', 'standup-comedy', 'Live comedy shows, improv nights, and open mic tours', 'Smile', 3, TRUE),
('Theatre & Performing Arts', 'theatre-arts', 'Stage plays, classical dance, musicals, and Broadway drama', 'Drama', 4, TRUE),
('Tech Conferences & Summits', 'tech-conferences', 'Developer meetups, AI summits, hackathons, and technology expos', 'Cpu', 5, TRUE)
ON DUPLICATE KEY UPDATE description = VALUES(description);

