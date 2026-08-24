-- =============================================================================
-- 05_seed_data.sql
-- Realistic sample data for the Hotel Booking System.
--
-- Volume: 2 countries, 5 cities, 3 room types, 10 users, 5 hotels, 20 rooms,
--         30 bookings, 20 payments, 15 reviews.
--
-- This script TRUNCATEs everything first and RESTARTs identity sequences, so it
-- is safe to re-run and produces deterministic IDs (1..N in insertion order).
-- All foreign keys, the no-overlap booking constraint, the paid_at/SUCCESS rule,
-- and the cancellation-consistency rule are respected.
--
-- "Today" in this dataset is around 2026-06-04:
--   * COMPLETED bookings are in the past (Jan–May 2026)
--   * CONFIRMED / PENDING bookings are upcoming (Jun–Aug 2026)
--   * CANCELLED and NO_SHOW bookings are excluded from the overlap constraint
-- =============================================================================

TRUNCATE TABLE
    reviews, payments, bookings, rooms, hotels, users, room_types, cities, countries
    RESTART IDENTITY CASCADE;

-- ----------------------------------------------------------------------------
-- countries (2)
-- ----------------------------------------------------------------------------
INSERT INTO countries (name, iso_code) VALUES
    ('India', 'IN'),          -- country_id = 1
    ('United States', 'US');  -- country_id = 2

-- ----------------------------------------------------------------------------
-- cities (5)
-- ----------------------------------------------------------------------------
INSERT INTO cities (country_id, name) VALUES
    (1, 'Mumbai'),         -- city_id = 1
    (1, 'Delhi'),          -- city_id = 2
    (1, 'Bangalore'),      -- city_id = 3
    (2, 'New York'),       -- city_id = 4
    (2, 'San Francisco');  -- city_id = 5

-- ----------------------------------------------------------------------------
-- room_types (3)
-- ----------------------------------------------------------------------------
INSERT INTO room_types (code, name, description, max_occupancy, base_price) VALUES
    ('STANDARD', 'Standard', 'Comfortable room with the essentials.',        2, 2500.00),  -- 1
    ('DELUXE',   'Deluxe',   'Spacious room with premium amenities.',        3, 4500.00),  -- 2
    ('SUITE',    'Suite',    'Luxury suite with separate living area.',      4, 8000.00);  -- 3

-- ----------------------------------------------------------------------------
-- users (10)
-- ----------------------------------------------------------------------------
-- password_hash is a placeholder; real hashing happens in the application.
INSERT INTO users (email, password_hash, full_name, phone) VALUES
    ('aarav.sharma@example.com',   '$2b$12$placeholderhashvalue000001', 'Aarav Sharma',   '+919800000001'),  -- 1
    ('diya.patel@example.com',     '$2b$12$placeholderhashvalue000002', 'Diya Patel',     '+919800000002'),  -- 2
    ('rohan.verma@example.com',    '$2b$12$placeholderhashvalue000003', 'Rohan Verma',    '+919800000003'),  -- 3
    ('ananya.iyer@example.com',    '$2b$12$placeholderhashvalue000004', 'Ananya Iyer',    '+919800000004'),  -- 4
    ('kabir.nair@example.com',     '$2b$12$placeholderhashvalue000005', 'Kabir Nair',     '+919800000005'),  -- 5
    ('isha.reddy@example.com',     '$2b$12$placeholderhashvalue000006', 'Isha Reddy',     '+919800000006'),  -- 6
    ('vivaan.singh@example.com',   '$2b$12$placeholderhashvalue000007', 'Vivaan Singh',   '+919800000007'),  -- 7
    ('myra.gupta@example.com',     '$2b$12$placeholderhashvalue000008', 'Myra Gupta',     '+919800000008'),  -- 8
    ('arjun.mehta@example.com',    '$2b$12$placeholderhashvalue000009', 'Arjun Mehta',    '+919800000009'),  -- 9
    ('saanvi.joshi@example.com',   '$2b$12$placeholderhashvalue000010', 'Saanvi Joshi',   '+919800000010'); -- 10

-- ----------------------------------------------------------------------------
-- hotels (5)
-- ----------------------------------------------------------------------------
INSERT INTO hotels (name, description, address_line, city_id, star_rating) VALUES
    ('The Taj Seaside',    'Iconic 5-star beachfront luxury.',        'Marine Drive, Nariman Point', 1, 5),  -- 1 Mumbai
    ('Capital Grand',      'Business hotel in the heart of Delhi.',   '12 Connaught Place',          2, 4),  -- 2 Delhi
    ('Garden City Inn',    'Cozy stay amid Bangalore greenery.',      '88 MG Road',                  3, 4),  -- 3 Bangalore
    ('Manhattan Plaza',    'Upscale tower with skyline views.',       '500 5th Avenue',              4, 5),  -- 4 New York
    ('Bayview Suites',     'Modern suites near the waterfront.',      '21 Embarcadero',              5, 4); -- 5 San Francisco

-- ----------------------------------------------------------------------------
-- rooms (20) — 4 per hotel
-- ----------------------------------------------------------------------------
INSERT INTO rooms (hotel_id, room_type_id, room_number, floor, nightly_price) VALUES
    -- Hotel 1 — The Taj Seaside (Mumbai)
    (1, 3, '101', 1, 8000.00),   -- room 1  Suite
    (1, 2, '102', 1, 5000.00),   -- room 2  Deluxe
    (1, 1, '201', 2, 3000.00),   -- room 3  Standard
    (1, 1, '202', 2, 3000.00),   -- room 4  Standard
    -- Hotel 2 — Capital Grand (Delhi)
    (2, 2, '301', 3, 4500.00),   -- room 5  Deluxe
    (2, 2, '302', 3, 4500.00),   -- room 6  Deluxe
    (2, 1, '303', 3, 2500.00),   -- room 7  Standard
    (2, 3, '401', 4, 7000.00),   -- room 8  Suite
    -- Hotel 3 — Garden City Inn (Bangalore)
    (3, 1, '11',  1, 2200.00),   -- room 9  Standard
    (3, 2, '12',  1, 4000.00),   -- room 10 Deluxe
    (3, 1, '13',  1, 2200.00),   -- room 11 Standard
    (3, 3, '21',  2, 6500.00),   -- room 12 Suite
    -- Hotel 4 — Manhattan Plaza (New York)
    (4, 2, '501', 5, 12000.00),  -- room 13 Deluxe
    (4, 3, '502', 5, 20000.00),  -- room 14 Suite
    (4, 1, '503', 5, 8000.00),   -- room 15 Standard
    (4, 1, '504', 5, 8000.00),   -- room 16 Standard
    -- Hotel 5 — Bayview Suites (San Francisco)
    (5, 3, '601', 6, 15000.00),  -- room 17 Suite
    (5, 2, '602', 6, 10000.00),  -- room 18 Deluxe
    (5, 1, '603', 6, 7000.00),   -- room 19 Standard
    (5, 1, '604', 6, 7000.00);   -- room 20 Standard

-- ----------------------------------------------------------------------------
-- bookings (30)
-- nights is GENERATED (not inserted). total_amount = nights * price_per_night.
-- ----------------------------------------------------------------------------
INSERT INTO bookings
    (booking_reference, user_id, room_id, check_in_date, check_out_date,
     price_per_night, total_amount, status, cancelled_at, cancellation_reason,
     created_at, updated_at) VALUES
    -- ===== COMPLETED (past stays, Jan–May 2026) — 16 rows =====
    ('BK-2026-0001',  1,  1, '2026-01-05', '2026-01-08',  8000.00,  24000.00, 'COMPLETED', NULL, NULL, '2025-12-28 10:00:00+00', '2025-12-28 10:00:00+00'),
    ('BK-2026-0002',  2,  2, '2026-01-10', '2026-01-12',  5000.00,  10000.00, 'COMPLETED', NULL, NULL, '2026-01-03 10:00:00+00', '2026-01-03 10:00:00+00'),
    ('BK-2026-0003',  3,  3, '2026-01-15', '2026-01-20',  3000.00,  15000.00, 'COMPLETED', NULL, NULL, '2026-01-08 10:00:00+00', '2026-01-08 10:00:00+00'),
    ('BK-2026-0004',  4,  5, '2026-02-01', '2026-02-04',  4500.00,  13500.00, 'COMPLETED', NULL, NULL, '2026-01-25 10:00:00+00', '2026-01-25 10:00:00+00'),
    ('BK-2026-0005',  5,  6, '2026-02-10', '2026-02-14',  4500.00,  18000.00, 'COMPLETED', NULL, NULL, '2026-02-03 10:00:00+00', '2026-02-03 10:00:00+00'),
    ('BK-2026-0006',  6,  9, '2026-02-15', '2026-02-18',  2200.00,   6600.00, 'COMPLETED', NULL, NULL, '2026-02-08 10:00:00+00', '2026-02-08 10:00:00+00'),
    ('BK-2026-0007',  7, 10, '2026-03-01', '2026-03-05',  4000.00,  16000.00, 'COMPLETED', NULL, NULL, '2026-02-22 10:00:00+00', '2026-02-22 10:00:00+00'),
    ('BK-2026-0008',  8, 13, '2026-03-10', '2026-03-12', 12000.00,  24000.00, 'COMPLETED', NULL, NULL, '2026-03-03 10:00:00+00', '2026-03-03 10:00:00+00'),
    ('BK-2026-0009',  9, 14, '2026-03-15', '2026-03-20', 20000.00, 100000.00, 'COMPLETED', NULL, NULL, '2026-03-08 10:00:00+00', '2026-03-08 10:00:00+00'),
    ('BK-2026-0010', 10, 17, '2026-04-01', '2026-04-03', 15000.00,  30000.00, 'COMPLETED', NULL, NULL, '2026-03-25 10:00:00+00', '2026-03-25 10:00:00+00'),
    ('BK-2026-0011',  1, 18, '2026-04-10', '2026-04-15', 10000.00,  50000.00, 'COMPLETED', NULL, NULL, '2026-04-03 10:00:00+00', '2026-04-03 10:00:00+00'),
    ('BK-2026-0012',  2,  1, '2026-04-20', '2026-04-25',  8000.00,  40000.00, 'COMPLETED', NULL, NULL, '2026-04-13 10:00:00+00', '2026-04-13 10:00:00+00'),
    ('BK-2026-0013',  3,  5, '2026-05-01', '2026-05-05',  4500.00,  18000.00, 'COMPLETED', NULL, NULL, '2026-04-24 10:00:00+00', '2026-04-24 10:00:00+00'),
    ('BK-2026-0014',  4,  9, '2026-05-10', '2026-05-14',  2200.00,   8800.00, 'COMPLETED', NULL, NULL, '2026-05-03 10:00:00+00', '2026-05-03 10:00:00+00'),
    ('BK-2026-0015',  5, 13, '2026-05-18', '2026-05-22', 12000.00,  48000.00, 'COMPLETED', NULL, NULL, '2026-05-11 10:00:00+00', '2026-05-11 10:00:00+00'),
    ('BK-2026-0016',  6,  2, '2026-05-25', '2026-05-28',  5000.00,  15000.00, 'COMPLETED', NULL, NULL, '2026-05-18 10:00:00+00', '2026-05-18 10:00:00+00'),
    -- ===== CONFIRMED (upcoming, paid) — 6 rows =====
    ('BK-2026-0017',  7,  3, '2026-06-10', '2026-06-15',  3000.00,  15000.00, 'CONFIRMED', NULL, NULL, '2026-05-30 10:00:00+00', '2026-05-30 10:00:00+00'),
    ('BK-2026-0018',  8,  7, '2026-06-20', '2026-06-25',  2500.00,  12500.00, 'CONFIRMED', NULL, NULL, '2026-06-01 10:00:00+00', '2026-06-01 10:00:00+00'),
    ('BK-2026-0019',  9, 11, '2026-07-01', '2026-07-05',  2200.00,   8800.00, 'CONFIRMED', NULL, NULL, '2026-06-02 10:00:00+00', '2026-06-02 10:00:00+00'),
    ('BK-2026-0020', 10, 15, '2026-07-10', '2026-07-14',  8000.00,  32000.00, 'CONFIRMED', NULL, NULL, '2026-06-02 10:00:00+00', '2026-06-02 10:00:00+00'),
    ('BK-2026-0021',  1, 19, '2026-07-20', '2026-07-25',  7000.00,  35000.00, 'CONFIRMED', NULL, NULL, '2026-06-03 10:00:00+00', '2026-06-03 10:00:00+00'),
    ('BK-2026-0022',  2,  4, '2026-08-01', '2026-08-05',  3000.00,  12000.00, 'CONFIRMED', NULL, NULL, '2026-06-03 10:00:00+00', '2026-06-03 10:00:00+00'),
    -- ===== PENDING (awaiting payment) — 3 rows =====
    ('BK-2026-0023',  3,  8, '2026-06-12', '2026-06-14',  7000.00,  14000.00, 'PENDING', NULL, NULL, '2026-06-03 10:00:00+00', '2026-06-03 10:00:00+00'),
    ('BK-2026-0024',  4, 12, '2026-06-18', '2026-06-20',  6500.00,  13000.00, 'PENDING', NULL, NULL, '2026-06-03 10:00:00+00', '2026-06-03 10:00:00+00'),
    ('BK-2026-0025',  5, 16, '2026-06-22', '2026-06-26',  8000.00,  32000.00, 'PENDING', NULL, NULL, '2026-06-04 10:00:00+00', '2026-06-04 10:00:00+00'),
    -- ===== CANCELLED (excluded from overlap constraint) — 3 rows =====
    ('BK-2026-0026',  6,  1, '2026-06-10', '2026-06-12',  8000.00,  16000.00, 'CANCELLED', '2026-06-03 09:00:00+00', 'Change of travel plans',      '2026-05-28 10:00:00+00', '2026-06-03 09:00:00+00'),
    ('BK-2026-0027',  7,  3, '2026-06-12', '2026-06-14',  3000.00,   6000.00, 'CANCELLED', '2026-06-02 09:00:00+00', 'Found a better rate',          '2026-05-30 10:00:00+00', '2026-06-02 09:00:00+00'),
    ('BK-2026-0028',  8, 20, '2026-05-01', '2026-05-03',  7000.00,  14000.00, 'CANCELLED', '2026-04-25 09:00:00+00', 'Trip postponed',               '2026-04-20 10:00:00+00', '2026-04-25 09:00:00+00'),
    -- ===== NO_SHOW (excluded from overlap constraint) — 2 rows =====
    ('BK-2026-0029',  9,  6, '2026-06-28', '2026-06-30',  4500.00,   9000.00, 'NO_SHOW', NULL, NULL, '2026-06-20 10:00:00+00', '2026-06-20 10:00:00+00'),
    ('BK-2026-0030', 10, 10, '2026-07-01', '2026-07-03',  4000.00,   8000.00, 'NO_SHOW', NULL, NULL, '2026-06-25 10:00:00+00', '2026-06-25 10:00:00+00');

-- ----------------------------------------------------------------------------
-- payments (20)
-- Rule reminder: paid_at IS NOT NULL  <=>  status = 'SUCCESS'.
-- ----------------------------------------------------------------------------
INSERT INTO payments
    (booking_id, direction, amount, currency, provider, provider_txn_id,
     status, paid_at, created_at, updated_at) VALUES
    -- Successful charges for COMPLETED bookings (1–13)
    ( 1, 'CHARGE',  24000.00, 'INR', 'RAZORPAY', 'rzp_txn_1001', 'SUCCESS', '2026-01-01 09:15:00+00', '2026-01-01 09:15:00+00', '2026-01-01 09:15:00+00'),
    ( 2, 'CHARGE',  10000.00, 'INR', 'RAZORPAY', 'rzp_txn_1002', 'SUCCESS', '2026-01-06 14:20:00+00', '2026-01-06 14:20:00+00', '2026-01-06 14:20:00+00'),
    ( 3, 'CHARGE',  15000.00, 'INR', 'STRIPE',   'str_txn_1003', 'SUCCESS', '2026-01-10 11:05:00+00', '2026-01-10 11:05:00+00', '2026-01-10 11:05:00+00'),
    ( 4, 'CHARGE',  13500.00, 'INR', 'RAZORPAY', 'rzp_txn_1004', 'SUCCESS', '2026-01-28 16:40:00+00', '2026-01-28 16:40:00+00', '2026-01-28 16:40:00+00'),
    ( 5, 'CHARGE',  18000.00, 'INR', 'STRIPE',   'str_txn_1005', 'SUCCESS', '2026-02-05 10:00:00+00', '2026-02-05 10:00:00+00', '2026-02-05 10:00:00+00'),
    ( 6, 'CHARGE',   6600.00, 'INR', 'RAZORPAY', 'rzp_txn_1006', 'SUCCESS', '2026-02-11 12:30:00+00', '2026-02-11 12:30:00+00', '2026-02-11 12:30:00+00'),
    ( 7, 'CHARGE',  16000.00, 'INR', 'RAZORPAY', 'rzp_txn_1007', 'SUCCESS', '2026-02-25 08:45:00+00', '2026-02-25 08:45:00+00', '2026-02-25 08:45:00+00'),
    ( 8, 'CHARGE',  24000.00, 'INR', 'STRIPE',   'str_txn_1008', 'SUCCESS', '2026-03-05 19:10:00+00', '2026-03-05 19:10:00+00', '2026-03-05 19:10:00+00'),
    ( 9, 'CHARGE', 100000.00, 'INR', 'STRIPE',   'str_txn_1009', 'SUCCESS', '2026-03-10 13:25:00+00', '2026-03-10 13:25:00+00', '2026-03-10 13:25:00+00'),
    (10, 'CHARGE',  30000.00, 'INR', 'RAZORPAY', 'rzp_txn_1010', 'SUCCESS', '2026-03-27 17:50:00+00', '2026-03-27 17:50:00+00', '2026-03-27 17:50:00+00'),
    (11, 'CHARGE',  50000.00, 'INR', 'STRIPE',   'str_txn_1011', 'SUCCESS', '2026-04-05 09:30:00+00', '2026-04-05 09:30:00+00', '2026-04-05 09:30:00+00'),
    (12, 'CHARGE',  40000.00, 'INR', 'RAZORPAY', 'rzp_txn_1012', 'SUCCESS', '2026-04-15 15:00:00+00', '2026-04-15 15:00:00+00', '2026-04-15 15:00:00+00'),
    (13, 'CHARGE',  18000.00, 'INR', 'RAZORPAY', 'rzp_txn_1013', 'SUCCESS', '2026-04-26 10:10:00+00', '2026-04-26 10:10:00+00', '2026-04-26 10:10:00+00'),
    -- Successful charges for CONFIRMED bookings (17, 18, 19)
    (17, 'CHARGE',  15000.00, 'INR', 'RAZORPAY', 'rzp_txn_1014', 'SUCCESS', '2026-06-01 11:00:00+00', '2026-06-01 11:00:00+00', '2026-06-01 11:00:00+00'),
    (18, 'CHARGE',  12500.00, 'INR', 'STRIPE',   'str_txn_1015', 'SUCCESS', '2026-06-02 14:00:00+00', '2026-06-02 14:00:00+00', '2026-06-02 14:00:00+00'),
    (19, 'CHARGE',   8800.00, 'INR', 'RAZORPAY', 'rzp_txn_1016', 'SUCCESS', '2026-06-03 09:00:00+00', '2026-06-03 09:00:00+00', '2026-06-03 09:00:00+00'),
    -- PENDING booking 23: payment attempt still INITIATED (not yet completed)
    (23, 'CHARGE',  14000.00, 'INR', 'RAZORPAY', 'rzp_txn_1017', 'INITIATED', NULL, '2026-06-03 18:00:00+00', '2026-06-03 18:00:00+00'),
    -- PENDING booking 24: payment FAILED (card declined) — booking stays pending
    (24, 'CHARGE',  13000.00, 'INR', 'STRIPE',   'str_txn_1018', 'FAILED',    NULL, '2026-06-03 19:00:00+00', '2026-06-03 19:00:00+00'),
    -- CANCELLED booking 26: was charged successfully, then refunded (two rows)
    (26, 'CHARGE',  16000.00, 'INR', 'RAZORPAY', 'rzp_txn_1019', 'SUCCESS', '2026-06-01 10:00:00+00', '2026-06-01 10:00:00+00', '2026-06-01 10:00:00+00'),
    (26, 'REFUND',  16000.00, 'INR', 'RAZORPAY', 'rzp_txn_1020', 'REFUNDED',  NULL, '2026-06-03 09:30:00+00', '2026-06-03 09:30:00+00');

-- ----------------------------------------------------------------------------
-- reviews (15) — only for COMPLETED stays (bookings 1–15).
-- user_id and hotel_id are denormalized but must match the booking's own values.
-- ----------------------------------------------------------------------------
INSERT INTO reviews (user_id, hotel_id, booking_id, rating, comment, created_at) VALUES
    ( 1, 1,  1, 5, 'Stunning sea view and impeccable service. Will return!',      '2026-01-09 08:00:00+00'),
    ( 2, 1,  2, 4, 'Great location, room slightly small but very clean.',          '2026-01-13 09:30:00+00'),
    ( 3, 1,  3, 5, 'Loved the breakfast spread and the staff hospitality.',        '2026-01-21 10:15:00+00'),
    ( 4, 2,  4, 4, 'Good business hotel, fast Wi-Fi, central location.',           '2026-02-05 07:45:00+00'),
    ( 5, 2,  5, 3, 'Comfortable but the AC was noisy at night.',                   '2026-02-15 11:00:00+00'),
    ( 6, 3,  6, 4, 'Peaceful garden setting, friendly reception.',                 '2026-02-19 18:20:00+00'),
    ( 7, 3,  7, 5, 'Excellent value for money. Highly recommended.',               '2026-03-06 14:10:00+00'),
    ( 8, 4,  8, 5, 'Incredible skyline views from the room. Worth every rupee.',   '2026-03-13 16:40:00+00'),
    ( 9, 4,  9, 4, 'Luxurious suite, though check-in took a while.',               '2026-03-21 09:05:00+00'),
    (10, 5, 10, 5, 'Modern, spotless suite near the waterfront. Perfect stay.',    '2026-04-04 12:30:00+00'),
    ( 1, 5, 11, 4, 'Spacious deluxe room, great location for sightseeing.',        '2026-04-16 08:50:00+00'),
    ( 2, 1, 12, 5, 'Second stay here and just as wonderful as the first.',         '2026-04-26 10:00:00+00'),
    ( 3, 2, 13, 3, 'Decent stay but breakfast options were limited.',             '2026-05-06 09:15:00+00'),
    ( 4, 3, 14, 4, 'Quiet, clean, and well-priced. Good for a short trip.',        '2026-05-15 17:25:00+00'),
    ( 5, 4, 15, 5, 'Top-notch service and an unbeatable Manhattan view.',          '2026-05-23 11:45:00+00');
